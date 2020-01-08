package com.wizzard.services;

import com.google.common.base.Strings;
import com.wizzard.dao.*;
import com.wizzard.dao.impl.SiteMongoDAO;
import com.wizzard.dao.impl.UserMongoDAO;
import com.wizzard.dao.impl.UserShiftMongoDAO;
import com.wizzard.dto.*;
import com.wizzard.model.*;
import com.wizzard.util.AmazonClient;
import com.wizzard.util.EmailService;
import com.wizzard.util.ServiceConstants;
import com.wizzard.util.ServiceUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.IteratorUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserManager {

    @Autowired
    private ShiftTemplateDAO shiftTemplateDAO;

    @Autowired
    private UserShiftDAO userShiftDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    UserShiftMongoDAO userShiftMongoDAO;

    @Autowired
    private UserShiftManager userShiftManager;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ClientManager clientManager;

    @Autowired
    private SiteManager siteManager;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private ResetPasswordDAO resetPasswordDAO;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ClientUserIdManager clientUserIdManager;

    @Autowired
    private AmazonClient amazonClient;

    @Autowired
    private UserMongoDAO userMongoDAO;

    @Autowired
    private UserProfileDAO userProfileDAO;

    @Autowired
    private UserShiftAssignmentsManager userShiftAssignmentsManager;

    @Autowired
    private ClientDAO clientDAO;

    @Autowired
    private SiteDAO siteDAO;

    @Autowired
    private UserActivationDAO userActivationDAO;

    @Autowired
    private SiteAdminsDAO siteAdminsDAO;

    @Autowired
    private SiteMongoDAO siteMongoDAO;

    /* @Autowired
    private ExcelParser excelParser;*/

    /**
       * Module to create otherShifts for user using a template
       * @param shiftTemplateId
       * @param userId
       * @param siteId
       * @param clientId
       * @param startDateStr
       * @param endDateStr
       * @param role
       * @param vehicleTypeId
     * @return
       * @throws ParseException
       */


    public List<UserShift> assignShiftToUser(String shiftTemplateId, String userId, String siteId, String clientId, String startDateStr, String endDateStr, int role, String vehicleTypeId) throws ParseException {
        UserShiftAssignments userShiftAssignments = userShiftAssignmentsManager.saveUserShiftAssignmentData(shiftTemplateId,userId,siteId,clientId,startDateStr,endDateStr,role);
        Date startDate = ServiceUtils.parseDate(startDateStr);
        Date endDate = ServiceUtils.parseDate(endDateStr);
        Calendar currentDate = Calendar.getInstance();
        currentDate.setTimeZone(ServiceUtils.getTimeZone());
        List<UserShift> userShiftList = new ArrayList<>();
        ShiftTemplate shiftTemplate = shiftTemplateDAO.findById(shiftTemplateId).get();
        List<Integer> weekDays = shiftTemplate.getWeekDays();
        Calendar startDate1 = Calendar.getInstance();
        startDate1.setTime(startDate);
        Calendar endDate1 = Calendar.getInstance();
        endDate1.setTime(endDate);
        endDate1.add(Calendar.DATE, 1);
        currentDate.set(Calendar.HOUR, shiftTemplate.getEndTime().getHours());
        currentDate.set(Calendar.MINUTE, shiftTemplate.getEndTime().getMinutes());
        if(currentDate.before(startDate1)){
            currentDate.setTime(startDate);
        }
        while(currentDate.before(endDate1)) {
            if(weekDays.size() != 0){
                int dayOfWeek = currentDate.get(Calendar.DAY_OF_WEEK);
                if(weekDays.contains(dayOfWeek)){
                    UserShift userShift = createShift(userId,shiftTemplate,currentDate,siteId,clientId,role,userShiftAssignments.getId(),vehicleTypeId);
                    if(userShift != null){
                        userShiftList.add(userShift);
                    }
                }
            }else{
                UserShift userShift = createShift(userId,shiftTemplate,currentDate,siteId,clientId,role,userShiftAssignments.getId(),vehicleTypeId);
                if(userShift != null){
                    userShiftList.add(userShift);
                }
            }
            currentDate.add(Calendar.DATE, 1);
        }
        userShiftList = IteratorUtils.toList(userShiftDAO.saveAll(userShiftList).iterator());
        return getShiftNamesMap(userShiftList);
    }

    private List<UserShift> getShiftNamesMap(List<UserShift> userShiftList){
        Map<String,String> clientNamesMap = clientManager.clientNamesMap();
        Map<String,String> siteNamesMap = siteManager.getSiteNames(true);
        Map<String,String> userNamesMap = getUserNames(true);
        userShiftList.stream().forEach(userShift -> {
            userShift.getAttributes().put("userName",userNamesMap.get(userShift.getUserId()));
            userShift.getAttributes().put("clientName",clientNamesMap.get(userShift.getClientId()));
            userShift.getAttributes().put("siteName",siteNamesMap.get(userShift.getSiteId()));
        });
        return userShiftList;
    }

    private UserShift createShift(String userId, ShiftTemplate shiftTemplate, Calendar currentDate, String siteId, String clientId, int role,String shiftAssignmentId,String vehicleTypeId){
        if(userShiftManager.isShiftExists(userId,shiftTemplate.getId(),ServiceUtils.formatDate(currentDate.getTime()),siteId)){
            return null;
        }
        UserShift userShift = new UserShift();
        userShift.setUserId(userId);
        userShift.setShiftTemplateId(shiftTemplate.getId());
        userShift.setStatus(UserShiftStatus.INIT);
        userShift.setClientId(shiftTemplate.getClientId());
        userShift.setShiftDate(currentDate.getTime());
        userShift.setShiftDateStr(ServiceUtils.formatDate(userShift.getShiftDate()));
        userShift.setStartTime(shiftTemplate.getStartTime());
        userShift.setEndTime(shiftTemplate.getEndTime());

        //create a new calender
        Calendar date = Calendar.getInstance();
        date.setTimeZone(ServiceUtils.getTimeZone());
        date.setTime(currentDate.getTime());
        date.set(Calendar.HOUR, shiftTemplate.getStartTime().getHours());
        date.set(Calendar.MINUTE, shiftTemplate.getStartTime().getMinutes());
        userShift.setExpectedStartTime(date.getTime());
        date.set(Calendar.HOUR, shiftTemplate.getEndTime().getHours());
        date.set(Calendar.MINUTE, shiftTemplate.getEndTime().getMinutes());
        userShift.setExpectedEndTime(date.getTime());

        userShift.setSiteId(siteId);
        userShift.setClientId(clientId);
        userShift.setUserRole(role);
        userShift.setUserShiftAssignmentId(shiftAssignmentId);
        userShift.setVehicleTypeId(vehicleTypeId);
        return userShift;
    }

    public UserLoginDTO getLoggedUserDetails() {
        String userId = sessionManager.getCurrentUser().getId();
        User user = userDAO.findById(userId).get();
        List<ClientUserId> clientUserIdList = clientUserIdManager.findClientIdsForUser(user.getId());
        UserLoginDTO loggedUser= new UserLoginDTO();
        loggedUser.setFullName(user.getFullName());
        loggedUser.setTotalDeliveries(user.getTotalDeliveries());
        loggedUser.setTotalDistance(user.getTotalDistance());
        loggedUser.setCashCollected(user.getCashCollected());
        loggedUser.setTotalTrips(user.getTotalTrips());
        loggedUser.setRole(user.getRole());
        loggedUser.setClientUserIdInfo(clientUserIdList);
        loggedUser.setZipZapTraining(user.isZipZapTraining());
        loggedUser.setClientTraining(user.isClientTraining());
        loggedUser.setFieldTraining(user.isFieldTraining());
        Calendar calendar = Calendar.getInstance();
        String dateStr = ServiceUtils.formatDate(calendar.getTime());
        List<UserShift> userShifts = userShiftMongoDAO.findUserShifts(userId,dateStr);
        Map<String,String> clientNamesMap = clientManager.clientNamesMap();
        Map<String,String> siteNamesMap = siteManager.getSiteNames(true);
        UserShift currentShift = userShiftMongoDAO.findCurrentUserShiftByDate(userId, dateStr);
        if(currentShift != null) {
            loggedUser.setCurrentShift(userShiftManager.getShiftDTO(clientNamesMap, siteNamesMap,currentShift));
        }
        if (userShifts.size() != 0) {
            List<ShiftDTO> otherShifts = new ArrayList<>();
            List<ShiftDTO> completedShifts = new ArrayList<>();
            userShifts.stream().forEach(shift -> {
                if(loggedUser.getCurrentShift() == null || !currentShift.getId().equalsIgnoreCase(shift.getId())){
                    if(shift.getStatus() == UserShiftStatus.SHIFT_ENDED ||
                            shift.getStatus() == UserShiftStatus.SHIFT_CLOSED_BY_SUPERVISOR ||
                            shift.getStatus() == UserShiftStatus.SHIFT_CANCELLED_BY_SUPERVISOR ) {
                        completedShifts.add(userShiftManager.getShiftDTO(clientNamesMap, siteNamesMap, shift));
                    } else {
                        otherShifts.add(userShiftManager.getShiftDTO(clientNamesMap, siteNamesMap, shift));
                    }
                }
            });
            loggedUser.setCompletedShifts(completedShifts);
            loggedUser.setOtherShifts(otherShifts);
        }else{
            if(user.getSiteIds() != null){
                List<String> siteIds = user.getSiteIds();
                List<JSONObject> siteSupervisorsInfo = new ArrayList<>();
                Set<String> siteSupervisorIds = siteMongoDAO.findSiteAdmins(siteIds);
                List<User> supervisorDetails = userMongoDAO.findUsersByIds(siteSupervisorIds);
                supervisorDetails.stream().forEach(supervisor -> {
                    JSONObject jsonObject = new JSONObject();
                    UserProfile userProfile = userProfileDAO.findProfileByUserId(supervisor.getId());
                    if(userProfile != null && userProfile.getProfilePicDetails() != null){
                        jsonObject.put("profilePicUrl",amazonClient.getSignedURL(ServiceConstants.S3_BUCKET_NAME, (String) userProfile.getProfilePicDetails().get("fileName")));
                    }
                    jsonObject.put("userName",supervisor.getFullName());
                    jsonObject.put("phoneNumber",supervisor.getPhoneNumber());
                    siteSupervisorsInfo.add(jsonObject);
                });
                loggedUser.setSiteSupervisorsInfo(siteSupervisorsInfo);
            }
        }
        return loggedUser;
    }

    public ResponseEntity<Object> addUser(User user) {
        List<String> errors = new ArrayList<>();
                Optional<User> existedUser = userDAO.findByUserNameAndDeleted(user.getUserName(),false);
                boolean phoneNumberExists = userDAO.existsByPhoneNumber(user.getPhoneNumber());
                if (!existedUser.isPresent()) {
                    if (!phoneNumberExists) {
                        user.setEnabled(true);
                        user.setAccountNonExpired(true);
                        user.setAccountNonLocked(true);
                        user.setPassword(encoder.encode(user.getPassword()));
                        user.setStatus(UserStatus.ACTIVATION_PENDING);
                        user = userDAO.save(user);
                        if(user.getRole() >= 20){
                            List<String> siteIds = user.getSiteIds();
                            userMongoDAO.createSiteAdmins(user.getId(),siteIds);
                        }
                        return ResponseEntity.ok().body(user);
                    } else {
                        errors.add("Duplicate PhoneNumber");
                        return new ResponseEntity<Object>(errors , HttpStatus.BAD_REQUEST);
                    }
                }
                errors.add("Duplicate userName");
                return new ResponseEntity<Object>(errors , HttpStatus.BAD_REQUEST);
    }

    public ResponseEntity<Object> updateUser(String id, User user) {
        List<String> errors = new ArrayList<>();
            User savedUser = userDAO.findById(id).get();
            Optional<User> existedUser = userDAO.findByUserName(user.getUserName());
            if (existedUser.isPresent()) {
                User tempUser = existedUser.get();
                if (tempUser.getId().equals(id)) {
                    savedUser.setFullName(user.getFullName());
                    savedUser.setUserName(user.getUserName());
                    savedUser.setEmail(user.getEmail());
                    savedUser.setAccountNonExpired(user.isAccountNonExpired());
                    savedUser.setAccountNonLocked(user.isAccountNonLocked());
                    savedUser.setEnabled(user.isEnabled());
                    savedUser.setSuperUser(user.isSuperUser());
                    savedUser.setStaff(user.isStaff());
                    savedUser.setActive(user.isActive());
                    savedUser.setDateJoined(user.getDateJoined());
                    savedUser.setSiteIds(user.getSiteIds());
                    savedUser.setCityIds(user.getCityIds());
                    savedUser.setZipZapTraining(user.isZipZapTraining());
                    savedUser.setClientTraining(user.isClientTraining());
                    savedUser.setFieldTraining(user.isFieldTraining());
                    savedUser.setPrimarySite(user.getPrimarySite());
                    if (user.getRole() > 0) {
                        savedUser.setRole(user.getRole());
                    }
                    /*if (user.getPhoneNumber() != null) {
                        if (Users.size() == 0 || Users.get(0).getId().equals(id)) {
                            savedUser.setPhoneNumber(user.getPhoneNumber());
                        } else {
                            errors.add("Duplicate PhoneNumber");
                            return new ResponseEntity<Object>(errors , HttpStatus.BAD_REQUEST);
                        }
                    }*/
                    savedUser.setTotalDistance(user.getTotalDistance());
                    savedUser.setTotalDeliveries(user.getTotalDeliveries());
                    savedUser.setTotalTrips(user.getTotalTrips());
                    savedUser.setPrimarySite(user.getPrimarySite());
                    savedUser = userDAO.save(savedUser);
                    if(savedUser.getRole() >= 20){
                        userMongoDAO.updateSiteAdmins(user);
                    }
                    return ResponseEntity.ok().body(savedUser);
                }else{
                    errors.add("Duplicate userName");
                    return new ResponseEntity<Object>(errors , HttpStatus.BAD_REQUEST);
                }
            } else {
                savedUser.setFullName(user.getFullName());
                savedUser.setUserName(user.getUserName());
                savedUser.setEmail(user.getEmail());
                savedUser.setAccountNonExpired(user.isAccountNonExpired());
                savedUser.setAccountNonLocked(user.isAccountNonLocked());
                savedUser.setEnabled(user.isEnabled());
                savedUser.setSuperUser(user.isSuperUser());
                savedUser.setStaff(user.isStaff());
                savedUser.setActive(user.isActive());
                savedUser.setDateJoined(user.getDateJoined());
                savedUser.setSiteIds(user.getSiteIds());
                savedUser.setCityIds(user.getCityIds());
                savedUser.setZipZapTraining(user.isZipZapTraining());
                savedUser.setClientTraining(user.isClientTraining());
                savedUser.setFieldTraining(user.isFieldTraining());
                if (user.getRole() != 0) {
                    savedUser.setRole(user.getRole());
                }
                /*if (Users.size() == 0 || Users.get(0).getId().equals(id)) {
                    savedUser.setPhoneNumber(user.getPhoneNumber());
                } else {
                    errors.add("Duplicate PhoneNumber");
                    return new ResponseEntity<Object>(errors , HttpStatus.BAD_REQUEST);
                }*/
                savedUser.setTotalDistance(user.getTotalDistance());
                savedUser.setTotalDeliveries(user.getTotalDeliveries());
                savedUser.setTotalTrips(user.getTotalTrips());
                savedUser = userDAO.save(savedUser);
                if(savedUser.getRole() >= 20){
                    userMongoDAO.updateSiteAdmins(user);
                }
                return ResponseEntity.ok().body(savedUser);
            }


    }

    public User getUser(String userId){
        return userDAO.findById(userId).get();
    }

    public Page<User> getUsersForSupervisors(JSONObject data, Pageable pageable){
        Page<User> users = userMongoDAO.getUsersForSupervisors(data,pageable);
        addDisplayDataForUserList(users);
        return  users;
    }

    private void addDisplayDataForUserList(Page<User> users) {
        List<String> userIds =
                users.getContent().stream()
                        .map(User::getId)
                        .collect(Collectors.toList());
        Map<String, String> clientUserIds = clientUserIdManager.findClientUserIdsForUsers(userIds);
        Map<String, String> shiftCounts = userShiftManager.findShiftCountsForUsers(userIds);

        Map<String, String> siteNames = siteManager.getSiteNames(true);
        StringBuilder sb = new StringBuilder();
        users.getContent().stream().forEach(user -> {
            if(user.getSiteIds() != null) {
                user.getSiteIds().stream().forEach(siteId -> {
                    if(sb.length() > 0){
                        sb.append(", ");
                    }
                    sb.append(siteNames.get(siteId));
                });
                user.getAttributes().put("sitesCount", String.valueOf(user.getSiteIds().size()));
            }
            String str = clientUserIds.get(user.getId());
            if(!Strings.isNullOrEmpty(str)){
                List<String> clientUserIdsArr = new ArrayList<String>(Arrays.asList(str.split(",")));
                user.getAttributes().put("clientUserIdsCount", String.valueOf(clientUserIdsArr.size()));
            }
            user.getAttributes().put("siteNames", sb.toString());
            user.getAttributes().put("clientUserIds", str);
            user.getAttributes().put("shiftsCount", shiftCounts.get(user.getId()));
            sb.setLength(0); //clear string builder
        });
    }

    public long getUsersForSupervisorsCount(JSONObject data) {
        return userMongoDAO.getUsersForSupervisorsCount(data);
    }
    public void deleteUser(String userId){
        userMongoDAO.deleteUser(userId);
        userShiftManager.suspendUserShifts(userId);
    }

    /**
     *  Get user names as a map
     * @param includeInactive
     * @return
     */
    public Map<String, String> getUserNames(boolean includeInactive) {
        List<User> users = IteratorUtils.toList(userDAO.findAll().iterator());
        if(users != null) {
            Map<String, String> map = users.stream().collect(
                    Collectors.toMap(User::getId, user -> user.getFullName()));
            return map;
        }
        return new HashMap<>();
    }

    public Map<String, String> getUserPhoneNumbers(boolean includeInactive) {
        List<User> users = IteratorUtils.toList(userDAO.findAll().iterator());
        if(users != null) {
            Map<String, String> map = users.stream().collect(
                    Collectors.toMap(User::getId, user -> user.getPhoneNumber()));
            return map;
        }
        return new HashMap<>();
    }

    public  JSONObject sendResetCode(String userName) throws IOException {
        Random rnd = new Random();
        int code = 100000 + rnd.nextInt(900000);
        JSONObject jsonObject = new JSONObject();
        User user = userDAO.findByUserName(userName).get();
        if(user == null){
            throw new RuntimeException("User not found");
        }
        jsonObject.put("message","Code to reset password has been sent to your registered email");
        ResetPassword obj = new ResetPassword();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, 10);
        obj.setUserName(userName);
        obj.setCode(code);
        obj.setExpiryTime(cal.getTime());
        resetPasswordDAO.save(obj);
        emailService.sendEmail(code,user.getEmail(),user.getFullName());
        return jsonObject;
    }

    public User resetUserPassword(String userName, String password, ResetPassword data) {
        return userMongoDAO.resetUserPassword(userName,password,data);
    }

    public User changeUserPassword(String password, String id) {
        return userMongoDAO.changeUserPassword(password,id);
    }

    public JSONArray getUserRoles() {
        JSONArray roles = new JSONArray();
        // to display only labourer role in the front end
        if(sessionManager.getCurrentUser().getRole() == Role.LABOURER.getValue()){
            roles.add(Role.LABOURER.toJSON());
            return roles;
        }
        roles = Role.getAllRoles();
        return roles;
    }

    public SupervisorDTO getSupervisorsInfo(){
        SupervisorDTO supervisorInfo = new SupervisorDTO();
        List<SiteDTO> supervisorSites = new ArrayList<>();
        List<UserShift> allShiftsList = new ArrayList<>();
        List<SiteAdmins> siteAdminsList = siteAdminsDAO.findAllByAdminId(sessionManager.getCurrentUser().getId());
//        List<Site> siteList = userMongoDAO.findSitesForSupervisor(sessionManager.getCurrentUser().getId());
        siteAdminsList.stream().forEach(siteAdmins -> {
            Site site = siteDAO.findById(siteAdmins.getSiteId()).get();
            SiteDTO siteDTO = new SiteDTO(site);
            List<UserShift> userShifts = userShiftManager.findShiftsAtSiteForToday(site.getId());
            userShifts.stream().forEach(userShift -> {
                Client client = clientDAO.findById(userShift.getClientId()).get();
                Site site1 = siteDAO.findById(userShift.getSiteId()).get();
                userShift.getAttributes().put("siteName",site1.getName());
                userShift.getAttributes().put("clientName",client.getClientName());
                userShift.getAttributes().put("SiteAddress",site1.getAddress());
                userShift.getAttributes().put("siteLatitude", String.valueOf(site1.getLatitude()));
                userShift.getAttributes().put("siteLongitude", String.valueOf(site1.getLongitude()));
                allShiftsList.add(userShift);
            });
            siteDTO.setShiftsCount(userShifts.size());
            supervisorInfo.setTotalShiftsCount(supervisorInfo.getTotalShiftsCount() + userShifts.size());
            supervisorSites.add(siteDTO);
        });
        supervisorInfo.setSupervisorSites(supervisorSites);
        supervisorInfo.setAllShifts(allShiftsList);
        return supervisorInfo;
    }

    public Page<User> getActivationPendingUsers(JSONObject data) {
        Page<User> users = userMongoDAO.getActivationPendingUsers(data);
        addDisplayDataForUserList(users);
        return users;
    }

    public long getPendingUsersCount(JSONObject jsonObject){
        return userMongoDAO.getPendingUsersCount(jsonObject);
    }

    public boolean activateUser(String userId, String remarks, int role, UserStatus status, String site) {
        return userMongoDAO.activateUser(userId,remarks,role,status,site);
    }

    public List<User> uploadUsersData(HttpServletRequest request) {
        try {
            MultipartHttpServletRequest mRequest = (MultipartHttpServletRequest) request;
            mRequest.getParameterMap();
            Iterator<String> itr = mRequest.getFileNames();
            while (itr.hasNext()) {
                MultipartFile mFile = mRequest.getFile(itr.next());
                String fileName = mFile.getOriginalFilename();
                amazonClient.uploadFile(ServiceConstants.S3_BUCKET_NAME, fileName,mFile);
//                return parseUsersDataSheet(fileName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

   /* private List<User> parseUsersDataSheet(String fileName) throws IOException, InvalidFormatException {
        InputStream stream = amazonClient.downloadFile(ServiceConstants.S3_BUCKET_NAME,fileName);
        return excelParser.excelParser(stream);
    }*/

   public List<UserShift> createShiftsForMultipleUsers(AssignShiftDTO shiftDTO) throws ParseException {
       List<String> userIds = shiftDTO.getUserIds();
       userIds.stream().forEach(userId -> {
           try {
               assignShiftToUser(shiftDTO.getShiftTemplateId(),userId,shiftDTO.getSiteId(),shiftDTO.getClientId(),shiftDTO.getStartDateStr(),shiftDTO.getEndDateStr(),shiftDTO.getRole(), shiftDTO.getVehicleTypeId());
           } catch (ParseException e) {
               e.printStackTrace();
           }
       });
       List<UserShift> userShiftList = userShiftMongoDAO.findAllShiftsByUserId(userIds,shiftDTO.getStartDateStr(),shiftDTO.getEndDateStr());
       return getShiftNamesMap(userShiftList);
   }

   public boolean unDeleteUser(String userId) {
       return userMongoDAO.unDeleteUser(userId);
   }

    public UserStatus getUserStatus() {
       return userMongoDAO.getUserStatus(sessionManager.getCurrentUser().getId());
    }

    public Page<User> getDeletedUsers(JSONObject data) {
        Page<User> users = userMongoDAO.getDeletedUsers(data);
        addDisplayDataForUserList(users);
        return  users;
    }

    public User findUser(String phoneNumber) {
       return userMongoDAO.findUser(phoneNumber);
    }

    public User getCurrentUser() {
       User user = sessionManager.getCurrentUser();
        String profilePicUrl = null;
        UserProfile userProfile = userProfileDAO.findProfileByUserId(user.getId());
        if(userProfile != null && userProfile.getProfilePicDetails() != null){
            profilePicUrl = amazonClient.getSignedURL(ServiceConstants.S3_BUCKET_NAME, (String) userProfile.getProfilePicDetails().get("fileName"));
        }
        user.getAttributes().put("profilePicUrl",profilePicUrl);
        user.getAttributes().put("roleValue", String.valueOf(user.getRole()));
       return user;
    }

    public boolean rejectUser(String userId, String reason) {
       return userMongoDAO.rejectUser(userId,reason);
    }

    public long getDeletedUsersCount() {
       return userMongoDAO.getDeletedUsersCount();
    }

    public long getRejectedUsersCount() {
       return userMongoDAO.getRejectedUsersCount();
    }

    public Page<User> getRejectedUsers(JSONObject data) {
        long total = getRejectedUsersCount();
        PageRequest pageable = PageRequest.of(0,10);
        if(data.get("size") != null && data.get("page") != null){
            int page = (int) data.get("page")-1;
            pageable = PageRequest.of(page,(int) data.get("size"));
        }
        List<User> users = userMongoDAO.getRejectedUsers(pageable);
        Map<String,String> userNamesMap = getUserNames(true);
        users.stream().forEach(user -> {
            UserActivation userActivation = userActivationDAO.findByUserId(user.getId());
            if(userActivation != null) {
                user.getAttributes().put("rejectedBy",userNamesMap.get(userActivation.getRejectedBy()));
                user.getAttributes().put("reason",userActivation.getRejectionReason());
            }
        });
        return new PageImpl<>(users, pageable, total);
    }

    public String getRefferalCode() {
       return sessionManager.getCurrentUser().getId();
    }

    public boolean reactivateUser(String userId) {
       return userMongoDAO.reactivateUser(userId);
    }
}
