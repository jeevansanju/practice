package com.wizzard.services;


import com.google.common.base.Strings;
import com.mongodb.client.result.UpdateResult;
import com.wizzard.dao.*;
import com.wizzard.dao.impl.UserProfileMongoDAO;
import com.wizzard.dao.impl.UserShiftMongoDAO;
import com.wizzard.dto.*;
import com.wizzard.exception.BadRequestException;
import com.wizzard.model.*;
import com.wizzard.util.AmazonClient;
import com.wizzard.util.ServiceConstants;
import com.wizzard.util.ServiceUtils;
import org.bson.Document;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
public class UserShiftManager {

    @Autowired
    private UserShiftDAO userShiftDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private SiteDAO siteDAO;

    @Autowired
    private AmazonClient amazonClient;

    @Autowired
    private UserProfileMongoDAO userProfileMongoDAO;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserShiftMongoDAO userShiftMongoDAO;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private UserAttendanceManager userAttendance;

    @Autowired
    private UserManager userManager;

    @Autowired
    private SiteManager siteManager;

    @Autowired
    private ClientManager clientManager;

    @Autowired
    private ShiftGeoLocationDAO shiftGeoLocationDAO;

    @Autowired
    private ClientDAO clientDAO;

    @Autowired
    private UserShiftAssignmentsManager userShiftAssignmentsManager;

    @Autowired
    private SiteAdminsDAO siteAdminsDAO;

    @Autowired
    private ShiftTemplateDAO shiftTemplateDAO;

    @Autowired
    private CityDAO cityDAO;

    @Autowired
    private ShiftTemplateManager shiftTemplateManager;

   /* @PostConstruct
    public void init() {
        *//**
     * Start all the unfinished shift alarm when the server is started
     *//*
        List<UserShiftAlarm> alarms = IteratorUtils.toList(userShiftAlarmDAO.findByFinished(false).iterator());
        alarms.forEach(alarm -> {
            UserShift userShift = userShiftDAO.findById(alarm.getShiftId()).get();
            startShiftAlarm(userShift);
        });
    }*/

    /**
     * Mark attendance by user or supervisor
     * @param shiftId
     * @param siteId
     * @param location
     * @return
     */
    public ShiftDTO markUserAttendance(String shiftId, String siteId,JSONObject location) {
        User currentUser = sessionManager.getCurrentUser();
        return markShiftAttendance(siteId, currentUser, null,shiftId,location);
    }

    public ShiftDTO markUserAttendanceBySupervisor(String shiftId,String siteId,String userId,JSONObject location) {
        User currentUser = userDAO.findById(userId).get();
        String supervisorId = sessionManager.getCurrentUser().getId();
        return markShiftAttendance(siteId,currentUser, supervisorId, shiftId, location);
    }
    private ShiftDTO markShiftAttendance(String siteId, User shiftUser, String supervisorId, String shiftId, JSONObject location) {
        ShiftDTO shift = null;
        Site site  = siteDAO.findById(siteId).get();
        UserShift userShift = userShiftDAO.findById(shiftId).get();
        if (userShift != null) {
            if (userShift.getSiteId().equals(siteId)) {
                if (userShift.getStatus() == UserShiftStatus.INIT) {
                    Date reportingTime = Calendar.getInstance().getTime();
                    userShift.setReportingTime(reportingTime);
                    Date expectedStartTime = userShift.getExpectedStartTime();
                    if(expectedStartTime != null) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(expectedStartTime);
                        cal.add(Calendar.MINUTE, 30);
                        expectedStartTime = cal.getTime();
                    } else {// set expectedStartTime -- needed for old shifts
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(userShift.getShiftDate());
                        cal.set(Calendar.MINUTE, userShift.getStartTime().getMinutes());
                        cal.set(Calendar.HOUR, userShift.getStartTime().getHours());
                        expectedStartTime = cal.getTime();
                        userShift.setExpectedStartTime(expectedStartTime);
                    }
                    if (reportingTime.before(expectedStartTime)) {
                        userShift.setReportedOnTime(true);
                    }
                    userShift.setStatus(UserShiftStatus.ATTENDANCE_MARKED);
                    if(supervisorId != null) { //set this only when attendance is marked by supervisor
                        userShift.setAttendanceMarkedBy(supervisorId);
                    }
                    if(location.get("latitude") != null && location.get("longitude") != null){
                        double latitude = Double.parseDouble(location.get("latitude").toString());
                        double longitude = Double.parseDouble(location.get("longitude").toString());
                        Boolean status = siteManager.verifySite(siteId,latitude,longitude);
                        userShift.setMarkedAtSite(status);
                    }
                    UserAttendanceDTO userAttendanceInfo = userAttendance.createUserAttendance(userShift.getId(),siteId, site.getLatitude(), site.getLongitude());
                    if (userAttendanceInfo != null) {
                        userShift.setUserAttendanceId(userAttendanceInfo.getUserAttendanceId());
                        userShift.setShiftActive(true);
                        userShift = userShiftDAO.save(userShift);
                        shift = new ShiftDTO(userShift);
                        shift.setUserAttendance(userAttendanceInfo);
                        if(shiftUser!=null){
                            shift.setUserRole(shiftUser.getRole());
                        }
                    } else {
                        throw new BadRequestException("Error marking user attendance");
                    }
                } else {
                    throw new BadRequestException("Wrong shift status");
                }
            } else {
                throw new BadRequestException("No shifts found for you at this site");
            }
        }
        return shift;
    }

    public UserShift markUserShiftActive(String userShiftId){
        if(userShiftMongoDAO.updateUserShiftActive(userShiftId)){
            return userShiftDAO.findById(userShiftId).get();
        } else {
            throw new BadRequestException("Failed to mark shift as active");
        }
    }

    public UserShift updateUserShift(String id,UserShift userShift){
        UserShift savedUserShift=userShiftDAO.findById(id).get();
        savedUserShift.setUserId(userShift.getUserId());
        savedUserShift.setShiftTemplateId(userShift.getShiftTemplateId());
        savedUserShift.setShiftDate(userShift.getShiftDate());
        savedUserShift.setShiftDateStr(userShift.getShiftDateStr());
        savedUserShift.setStartTime(userShift.getStartTime());
        savedUserShift.setEndTime(userShift.getEndTime());
        savedUserShift.setActualStartTime(userShift.getActualStartTime());
        savedUserShift.setActualEndTime(userShift.getActualEndTime());
        savedUserShift.setReportingTime(userShift.getReportingTime());
        savedUserShift.setClientId(userShift.getClientId());
        savedUserShift.setSiteId(userShift.getSiteId());
        savedUserShift.setStatus(userShift.getStatus());
        savedUserShift.setTotalPackages(userShift.getTotalPackages());
        savedUserShift.setTotalDeliveries(userShift.getTotalDeliveries());
        savedUserShift.setUserComments(userShift.getUserComments());
        savedUserShift.setAdminComments(userShift.getAdminComments());
        savedUserShift.setClientComments(userShift.getClientComments());
        savedUserShift.setUserAttendanceId(userShift.getUserAttendanceId());
        savedUserShift.setStartOdometerReading(userShift.getStartOdometerReading());
        savedUserShift.setEndOdometerReading(userShift.getEndOdometerReading());
        savedUserShift.setCashCollected(userShift.getCashCollected());
        return userShiftDAO.save(savedUserShift);
    }
    public UserShift getUserShift(String id){
        UserShift userShift = userShiftDAO.findById(id).get();
        if(userShift==null){
            throw new BadRequestException("No UserShift found");
        }

        if(userShift.getExpectedEndTime() == null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(userShift.getShiftDate());
            calendar.set(Calendar.HOUR, userShift.getEndTime().getHours());
            calendar.set(Calendar.MINUTE, userShift.getEndTime().getMinutes());
            userShift.setExpectedEndTime(calendar.getTime());
            userShiftMongoDAO.updateShift(userShift.getId(), "expectedEndTime", userShift.getExpectedEndTime());
            userShift = userShiftDAO.findById(userShift.getId()).get();
        }
        Site site = siteDAO.findById(userShift.getSiteId()).get();
        userShift.getAttributes().put("userName",userDAO.findById(userShift.getUserId()).get().getFullName());
        userShift.getAttributes().put("reportedTime", ServiceUtils.formatDate(userShift.getReportingTime()));
        userShift.getAttributes().put("shiftStartTime", ServiceUtils.formatDate(userShift.getActualStartTime()));
        userShift.getAttributes().put("shiftEndTime", ServiceUtils.formatDate(userShift.getActualEndTime()));
        //userShift.getAttributes().put("duration", hms);
        userShift.getAttributes().put("site",site.getName());
        userShift.getAttributes().put("siteCode",site.getSiteCode());
        Optional<City> city = cityDAO.findById(site.getCityId());
        if(city.isPresent()) {
            userShift.getAttributes().put("siteCity",city.get().getName());
        }
        Optional<Client> client = clientDAO.findById(userShift.getClientId());
        if(client.isPresent()) {
            userShift.getAttributes().put("client",client.get().getClientName());
        }
        //userShift.getAttributes().put("shiftDuration",String.valueOf(shiftDuration));
        return userShift;
    }

    public Page<UserShift> search(JSONObject query , Pageable pageable) throws ParseException {
        Page<UserShift>  userShifts = userShiftMongoDAO.search(query, pageable);
        Map<String, String> userNames = userManager.getUserNames(true);
        Map<String, String> siteNames = siteManager.getSiteNames(true);
        Map<String, String> clientNames = clientManager.clientNamesMap();
        Map<String,String> userMobileNumbers = userManager.getUserPhoneNumbers(true);
        userShifts.stream().forEach(us -> {
            us.getAttributes().put("userName", userNames.get(us.getUserId()));
            us.getAttributes().put("siteName", siteNames.get(us.getSiteId()));
            us.getAttributes().put("clientName", clientNames.get(us.getClientId()));
            us.getAttributes().put("phoneNumber",userMobileNumbers.get(us.getUserId()));
        });
        return userShifts;
    }

    public long count(JSONObject query) throws ParseException {
        return userShiftMongoDAO.count(query);
    }
    public boolean isShiftExists(String userId, String shiftTemplateId, String shiftDate, String siteId) {
        final Query query = new Query();
        query.addCriteria(where("userId").is(userId));
        query.addCriteria(where("shiftTemplateId").is(shiftTemplateId));
        query.addCriteria(where("shiftDateStr").is(shiftDate));
        query.addCriteria(where("siteId").is(siteId));
        List<UserShift> shifts = mongoTemplate.find(query,UserShift.class);
        return shifts.size() > 0;
    }
    public ResponseEntity<Object> startUserShift(String shiftId, JSONObject data){
        List<String> errors = new ArrayList<>();
        UserShift shift=userShiftDAO.findById(shiftId).get();
        if(shift!=null){
            if(shift.getStatus()== UserShiftStatus.ATTENDANCE_MARKED){
                UserShift userShift=userShiftMongoDAO.startUserShift(shiftId,data);

                if(userShift == null){
                    errors.add("error while updating");
                    return new ResponseEntity<Object>(errors , HttpStatus.BAD_REQUEST);
                }
                return ResponseEntity.ok().body(userShift);
            }else{
                errors.add("Attendence is Not Marked");
                return new ResponseEntity<Object>(errors , HttpStatus.BAD_REQUEST);
            }
        } else {
            errors.add("No Shifts Found");
            return new ResponseEntity<Object>(errors , HttpStatus.BAD_REQUEST);
        }
    }

    public ResponseEntity<Object> endUserShift(String shiftId, JSONObject data){
        double endOdometerReading = 0.0;
        UserShift userShift = userShiftDAO.findById(shiftId).get();
        if(userShift.getStatus() != UserShiftStatus.SHIFT_IN_PROGRESS) {
            throw new BadRequestException("Invalid shift status " + userShift.getStatus());
        }
        if(data.get("endOdometerReading") != null){
            endOdometerReading=Double.parseDouble((String) data.get("endOdometerReading"));
            if(userShift.getStartOdometerReading() > endOdometerReading) {
                throw new BadRequestException("End odometer reading should be greater than start reading");
            }
        }
        Map<String,String> clientNamesMap = clientManager.clientNamesMap();
        Map<String,String> siteNamesMap = siteManager.getSiteNames(true);
        ShiftDTO currentUserShift = getShiftDTO(clientNamesMap, siteNamesMap, userShiftMongoDAO.endShift(userShift,data));
        return ResponseEntity.ok().body(currentUserShift);
    }

    public boolean updateShiftStatus(String id, String status) {
        return userShiftMongoDAO.updateShiftStatus(id, status);
    }

    /**
     * create an Ad-HOC shift to the user
     * @param userId
     * @param shift
     * @return
     */
    public UserShift createAdhocShift(String userId, UserShift shift) {
        UserShiftAssignments userShiftAssignments = userShiftAssignmentsManager.saveUserShiftAssignmentData(null,userId,shift.getSiteId(),
                shift.getClientId(),null,null,shift.getUserRole());
        shift.setUserId(userId);
        shift.setStatus(UserShiftStatus.INIT);
        shift.setShiftDateStr(ServiceUtils.formatDate(shift.getShiftDate()));
        shift.setUserShiftAssignmentId(userShiftAssignments.getId());
        //set expected end time
        Calendar date = Calendar.getInstance();
        date.setTime(shift.getShiftDate());
        date.set(Calendar.HOUR, shift.getStartTime().getHours());
        date.set(Calendar.MINUTE, shift.getStartTime().getMinutes());
        shift.setExpectedStartTime(date.getTime());
        date.set(Calendar.HOUR, shift.getEndTime().getHours());
        date.set(Calendar.MINUTE, shift.getEndTime().getMinutes());
        shift.setExpectedEndTime(date.getTime());

        UserShift userShift = userShiftDAO.save(shift);
        userShift.getAttributes().put("userName",userDAO.findById(userId).get().getFullName());
        userShift.getAttributes().put("siteName",siteDAO.findById(shift.getSiteId()).get().getName());
        userShift.getAttributes().put("clientName",clientDAO.findById(shift.getClientId()).get().getClientName());
        return userShift;
    }

    /**
     * Module to build SiteDTO
     * @param clientNamesMap
     * @param siteNamesMap
     * @param shift
     * @return
     */
    public ShiftDTO getShiftDTO(Map<String, String> clientNamesMap, Map<String, String> siteNamesMap,UserShift shift) {
        ShiftDTO shiftDTO = new ShiftDTO(shift);
        shiftDTO.getAttributes().put("clientName",clientNamesMap.get(shift.getClientId()));
        shiftDTO.getAttributes().put("siteName",siteNamesMap.get(shift.getSiteId()));
        if (shift.getSiteId() != null) {
            Site site = siteDAO.findById(shift.getSiteId()).get();
            if (site != null) {
                List<SiteAdmins> siteAdminsList = siteAdminsDAO.findAllBySiteId(site.getId());
                List<String> siteAdministrators = new ArrayList<>();
                siteAdminsList.stream().forEach(siteAdmins -> {
                    siteAdministrators.add(siteAdmins.getAdminId());
                });
                SiteDTO siteDTO = new SiteDTO();
                siteDTO.setName(site.getName());
                siteDTO.setSiteId(site.getId());
                siteDTO.setAddress(site.getAddress());
                siteDTO.setLatitude(site.getLatitude());
                siteDTO.setLongitude(site.getLongitude());
                siteDTO.setSiteAdministratorIds(siteAdministrators);
                siteDTO.setAddress(site.getAddress());
                if (siteAdministrators != null) {
                    List<SupervisorDTO> supervisorDTOList = new ArrayList<>();
                    for (int j = 0; j < siteAdministrators.size(); j++) {
                        Optional<User> siteAdministrator = userDAO.findById(siteAdministrators.get(j));
                        if (siteAdministrator.isPresent()) {
                            SupervisorDTO supervisorDTO = new SupervisorDTO();
                            supervisorDTO.setSupervisorName(siteAdministrator.get().getFullName());
                            supervisorDTO.setPhoneNumber(siteAdministrator.get().getPhoneNumber());
                            UserProfile supervisorProfile = userProfileMongoDAO.findProfileByUserId(siteAdministrator.get().getId());
                            if (supervisorProfile != null) {
                                JSONObject profilePicInfo = supervisorProfile.getProfilePicDetails();
                                if (profilePicInfo != null && profilePicInfo.size() != 0) {
                                    String profilePicURL = amazonClient.getSignedURL(ServiceConstants.S3_BUCKET_NAME,
                                            profilePicInfo.get("fileName").toString());
                                    supervisorDTO.setProfilePicURL(profilePicURL);
                                }
                            }
                            supervisorDTOList.add(supervisorDTO);
                        }
                    }
                    siteDTO.setSupervisor(supervisorDTOList);
                }
                UserAttendanceDTO userAttendanceDTO = new UserAttendanceDTO();
                userAttendanceDTO.setSiteDTO(siteDTO);
                shiftDTO.setUserAttendance(userAttendanceDTO);
            }
        }
        return shiftDTO;
    }

    public ShiftGeoLocation saveShiftLocation(ShiftGeoLocation data) {
        return shiftGeoLocationDAO.save(data);
    }

    public UserLoginDTO selectShift(String currentShiftId, String newShiftId) {
        UserShift currentShift = userShiftDAO.findById(currentShiftId).get();
        if(currentShift.getStatus() == UserShiftStatus.INIT){
            final Query query = new Query();
            query.addCriteria(where("_id").is(currentShiftId));
            Update updateOp = new Update();
            updateOp.set("shiftActive",false);
            UpdateResult updateResult = mongoTemplate.updateMulti(query,updateOp,UserShift.class);
            final Query query1 = new Query();
            query1.addCriteria(where("_id").is(newShiftId));
            Update updateOp1 = new Update();
            updateOp1.set("shiftActive",true);
            UpdateResult updateResult1 = mongoTemplate.updateMulti(query1,updateOp1,UserShift.class);
            UserLoginDTO loggedUserDetails = userManager.getLoggedUserDetails();
            return loggedUserDetails;
        }
        return null;
    }

    public Page<UserShift> getTrips(JSONObject data) throws ParseException {
        Query query = userShiftMongoDAO.tripsQuery(data);
        long total = mongoTemplate.count(query,UserShift.class);
        PageRequest pageable = PageRequest.of(0,25);
        if(data.get("size") != null && data.get("page") != null){
            int page = (int) data.get("page")-1;
            pageable = PageRequest.of(page,(int) data.get("size"));
        }
        query.with(pageable);
        List<UserShift> userShiftList = mongoTemplate.find(query,UserShift.class);
        Map<String,String> siteNamesMap = siteManager.getSiteNames(true);
        Map<String,String> clientNamesMap = clientManager.clientNamesMap();
        userShiftList.stream().forEach(userShift -> {
            String dateStr = userShift.getShiftDateStr();
            String[] parts = dateStr.split("-");
            String shiftDate =  parts[2]+"/"+parts[1]+"/"+parts[0];
            userShift.getAttributes().put("siteName",siteNamesMap.get(userShift.getSiteId()));
            userShift.getAttributes().put("clientName",clientNamesMap.get(userShift.getClientId()));
            userShift.getAttributes().put("shiftDate",shiftDate);
        });
        return new PageImpl<>(userShiftList, pageable, total);
    }

    public long getTripsCount(JSONObject data) throws ParseException {
        Query query = userShiftMongoDAO.tripsQuery(data);
        return mongoTemplate.count(query,UserShift.class);
    }

    public List<ShiftGeoLocation> getShiftGeoLocations(String shiftId){
        List<ShiftGeoLocation> shiftGeoLocations = shiftGeoLocationDAO.findByShiftId(shiftId);
        shiftGeoLocations.stream().forEach(location -> {
            JSONObject coordinates = new JSONObject();
            coordinates.put("latitude",location.getLatitude());
            coordinates.put("longitude",location.getLongitude());
            location.getAttributes().put("coordinates", String.valueOf(coordinates));
        });
        return shiftGeoLocations;
    }

    public List<UserShift> findShiftsAtSiteForToday(String siteId){
        List<UserShift> userShiftList = userShiftMongoDAO.findShiftsAtSiteForToday(siteId);
        userShiftList.stream().forEach(userShift -> {
            User user = userDAO.findById(userShift.getUserId()).get();
            UserProfile userProfile = userProfileMongoDAO.findProfileByUserId(user.getId());
            if(userProfile != null){
                JSONObject jsonObject = userProfile.getProfilePicDetails();
                if(jsonObject != null) {
                    userShift.getAttributes().put("profilePicUrl",
                            amazonClient.getSignedURL(ServiceConstants.S3_BUCKET_NAME, (String) jsonObject.get("fileName")));
                } else {
                    userShift.getAttributes().put("profilePicUrl",null);
                }
            }else{
                userShift.getAttributes().put("profilePicUrl",null);
            }
            userShift.getAttributes().put("phoneNumber",user.getPhoneNumber());
            userShift.getAttributes().put("fullName",user.getFullName());
        });
        return userShiftList;
    }


    public UserShift startShiftBySupervisor(String shiftId, JSONObject data) {
        if(sessionManager.getCurrentUser().getRole() >= 20){
            data.put("shiftStartedBy",sessionManager.getCurrentUser().getId());
            return userShiftMongoDAO.startUserShift(shiftId,data);
        }else{
            throw new BadRequestException("Only site supervisor can start the shift");
        }
    }

    public UserShift endShiftBySupervisor(String shiftId, JSONObject data) {
        if(sessionManager.getCurrentUser().getRole() >= 20){
            data.put("shiftEndedBy",sessionManager.getCurrentUser().getId());
            return userShiftMongoDAO.endShift(userShiftDAO.findById(shiftId).get(),data);
        }else{
            throw new BadRequestException("Only site supervisor can edit the shift");
        }
    }


    public JSONObject cancelShift(String shiftId,String cancellationReason) {
        JSONObject jsonObject = new JSONObject();
        if (sessionManager.getCurrentUser().getRole() == Role.SITE_SUPERVISOR.getValue()) {
            UserShift userShift = userShiftDAO.findById(shiftId).get();
            if (userShift.getStatus() == UserShiftStatus.INIT) {
                userShiftMongoDAO.cancelShift(shiftId, cancellationReason);
                jsonObject.put("message","Shift cancelled");
                return jsonObject;
            }else if(userShift.getStatus() == UserShiftStatus.SHIFT_CANCELLED_BY_SUPERVISOR){
                throw new BadRequestException("Shift is already Cancelled");
            }else {
                throw new BadRequestException("Shift cannot be cancelled");
            }
        } else {
            throw new BadRequestException("Shift cannot be cancelled");
        }
    }

    public boolean updateClientUserId(String shiftId, JSONObject clientUserIdInfo) {
        return userShiftMongoDAO.updateClientUserId(shiftId,clientUserIdInfo);
    }

    public List<Document> aggregateUserShiftData() {
        return userShiftMongoDAO.aggregateUserShiftData();
    }

    public JSONObject getShiftsSummary(String shiftDate , String siteId) {
        return userShiftMongoDAO.getShiftsSummary(shiftDate,siteId);
    }

    /**
     * To update packages count by supervisor
     * @param shiftId
     * @param packages
     * @return
     */
    public UserShift updatePackagesInfo(String shiftId, JSONObject packages) {
        if(sessionManager.getCurrentUser().getRole() >= 20){
            return userShiftMongoDAO.updatePackagesInfo(shiftId,packages);
        }else{
            throw new BadRequestException("Only site supervisor can edit the packages");
        }
    }
    /**
     * Module to create an Alarm to end the shift that is already started
     * @param userShift
     */
    /*
    public void startShiftAlarm(UserShift userShift) {
        TaskScheduler scheduler;
        ScheduledExecutorService localExecutor = Executors.newSingleThreadScheduledExecutor();
        scheduler = new ConcurrentTaskScheduler(localExecutor);
        UserShiftAlarm userShiftAlarm = userShiftAlarmDAO.save(
                new UserShiftAlarm(userShift.getId(),userShift.getExpectedEndTime()));
        scheduler.schedule(new ShiftMonitor(userShiftAlarm.getId(),userShiftMongoDAO, userShiftAlarmDAO,
                userShiftDAO),userShift.getExpectedEndTime());
    }*/

    /**
     * Module to find shifts count for users for current date
     * @param userIds
     * @return
     */
    public Map<String, String> findShiftCountsForUsers(List<String> userIds) {
        return userShiftMongoDAO.findShiftCountsForUsers(userIds);
    }

    public List<UserShift> getShiftsToUpdate(JSONObject data) throws ParseException {
        List<UserShift> userShifts = userShiftMongoDAO.getShiftsToUpdate(data);
        Map<String, String> userNames = userManager.getUserNames(true);
        Map<String, String> siteNames = siteManager.getSiteNames(true);
        Map<String, String> clientNames = clientManager.clientNamesMap();
        Map<String,String> templateNames = shiftTemplateManager.shiftTemplateNamesMap();
        userShifts.stream().forEach(us -> {
            us.getAttributes().put("userName", userNames.get(us.getUserId()));
            us.getAttributes().put("siteName", siteNames.get(us.getSiteId()));
            us.getAttributes().put("clientName", clientNames.get(us.getClientId()));
            us.getAttributes().put("templateName",templateNames.get(us.getShiftTemplateId()));
        });
        return userShifts;
    }

    public boolean updateShifts(JSONObject data) {
        List<String> shiftIds = (List<String>) data.get("shiftIds");
        int role = 0;
        if(data.containsKey("role")){
            role = Integer.parseInt(data.get("role").toString());
        }
        if(data.containsKey("templateId") && !Strings.isNullOrEmpty(data.get("templateId").toString())){
            ShiftTemplate shiftTemplate =  shiftTemplateDAO.findById(data.get("templateId").toString()).get();
            if(shiftIds.size() > 0){
                for(String shiftId:shiftIds){
                    UserShift userShift = userShiftDAO.findById(shiftId).get();
                    Date shiftDate = userShift.getShiftDate();
                    userShiftMongoDAO.updateShift(new ArrayList<>(),shiftId,shiftTemplate,shiftDate,role);
                }
            }
        }else{
            userShiftMongoDAO.updateShift(shiftIds,null,null,null,role);
        }
        return true;
    }
    public void suspendUserShifts(String userId){
         userShiftMongoDAO.suspendUserShift(userId);
    }

}
