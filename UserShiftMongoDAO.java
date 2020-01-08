package com.wizzard.dao.impl;

import com.google.common.base.Strings;
import com.mongodb.client.result.UpdateResult;
import com.wizzard.dao.SiteAdminsDAO;
import com.wizzard.dao.UserShiftDAO;
import com.wizzard.dto.PackageTypesDTO;
import com.wizzard.exception.BadRequestException;
import com.wizzard.model.*;
import com.wizzard.services.SessionManager;
import com.wizzard.util.ServiceUtils;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.joda.time.DateTime;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
@Slf4j
public class UserShiftMongoDAO {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserShiftDAO userShiftDAO;

    @Autowired
    private UserMongoDAO userMongoDAO;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private SiteAdminsDAO siteAdminsDAO;

    public List<UserShift> findUserShifts(String userId,  String dateStr){
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("shiftDateStr").is(dateStr));
        List<UserShift> userShifts = mongoTemplate.find(query, UserShift.class);
        return userShifts;
    }

    /**
     *
     * @param userId
     * @param dateStr
     * @return
     */
    public UserShift findCurrentUserShiftByDate(String userId, String dateStr){
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("shiftDateStr").is(dateStr));
        query.addCriteria(Criteria.where("status").ne("SHIFT_ENDED"));
        List<UserShift> userShifts = mongoTemplate.find(query, UserShift.class);
        if(userShifts.size() == 1){
            return userShifts.get(0);
        } else if(userShifts.size() > 1){
            List<UserShift> inactiveShifts = new ArrayList<>();
            for(UserShift userShift: userShifts){
                if( userShift.isShiftActive() && userShift.getStatus() != UserShiftStatus.SHIFT_ENDED){
                    return userShift;
                }
                if(!userShift.isShiftActive()){
                    inactiveShifts.add(userShift);
                }
            }
            if(inactiveShifts.size() == 1) {
                return inactiveShifts.get(0);
            }
        }
        return null;
    }

    public UserShift startUserShift(String id, JSONObject data){
        final Query query = new Query();
        query.addCriteria(where("_id").is(id));
        Calendar calendar=Calendar.getInstance();
        Update updateOp = new Update();
        updateOp.set("status", UserShiftStatus.SHIFT_IN_PROGRESS);
        if(data.get("startOdometerReading")!=null){
            updateOp.set("startOdometerReading",Double.parseDouble((String) data.get("startOdometerReading")));
        }
        if(data.get("pickUpPackagesInfo") != null){
           /* Map<String,Integer> pickUpPackagesInfo = new HashMap<>();
            Object packagesList = data.get("pickUpPackagesInfo");
            if( packagesList instanceof  List){
                List<JSONObject> packagesData = (List<JSONObject>) data.get("pickUpPackagesInfo");
                for(int i=0;i<packagesData.size();i++){
                    Map<String,String> jsonObject = packagesData.get(i);
                    pickUpPackagesInfo.put(jsonObject.get("type").toString(), Integer.valueOf(String.valueOf(jsonObject.get("count"))));
                }
            } else if (packagesList instanceof  Map){
                pickUpPackagesInfo = (Map<String, Integer>) data.get("pickUpPackagesInfo");
            }*/
            long pickUpPackagesCount = 0;
            List<PackageTypesDTO> pickUpPackagesInfo = (List<PackageTypesDTO>) data.get("pickUpPackagesInfo");
            for(int i=0;i<pickUpPackagesInfo.size();i++){
                JSONObject jsonObject = new JSONObject((Map) pickUpPackagesInfo.get(i));
                pickUpPackagesCount += Long.parseLong(jsonObject.get("count").toString());
            }
            updateOp.set("pickUpPackagesInfo",pickUpPackagesInfo);
            updateOp.set("pickUpPackagesCount",pickUpPackagesCount);
        }
        if(data.get("clientUserIdInfo") != null){
            updateOp.set("clientUserIdInfo",data.get("clientUserIdInfo"));
        }
        if(data.get("shiftStartedBy") != null){
            updateOp.set("shiftStartedBy",data.get("shiftStartedBy").toString());
        }
        updateOp.set("actualStartTime",calendar.getTime());
        UpdateResult writeResult =  mongoTemplate.updateMulti(query, updateOp, UserShift.class);
        if(writeResult.getModifiedCount() != 1){
            throw new BadRequestException("Failed to start shift");
        }
        UserShift userShift = userShiftDAO.findById(id).get();
        return userShift;
    }
    public UserShift endShift(UserShift userShift, JSONObject data){
        final Query query = new Query();
        Calendar calendar = Calendar.getInstance();
        query.addCriteria(where("_id").is(userShift.getId()));
        Update updateOp = new Update();
        double tripDistance = 0;
        double totalDeliveries = 0;
        double cashCollected = 0;

        long shiftDuration = 0;
        if(userShift.getStatus() == UserShiftStatus.SHIFT_IN_PROGRESS) {
            if(userShift.getExpectedEndTime() != null && userShift.getReportingTime() != null) {
                shiftDuration = userShift.getExpectedEndTime().getTime() - userShift.getReportingTime().getTime();
            }
        } else if(userShift.getStatus() == UserShiftStatus.SHIFT_CLOSED_BY_SUPERVISOR ||
                userShift.getStatus() == UserShiftStatus.SHIFT_ENDED) {
            shiftDuration = calendar.getTime().getTime() - userShift.getReportingTime().getTime();
        }
        String hms = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(shiftDuration),
                TimeUnit.MILLISECONDS.toMinutes(shiftDuration) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(shiftDuration)),
                TimeUnit.MILLISECONDS.toSeconds(shiftDuration) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(shiftDuration)));
        updateOp.set("duration",hms);


        if(data.get("endOdometerReading") != null){
            double endOdometerReading = Double.parseDouble((String) data.get("endOdometerReading"));
            updateOp.set("endOdometerReading", endOdometerReading);
            tripDistance = endOdometerReading - userShift.getStartOdometerReading();
        }
        if(data.get("cashCollected") != null){
            cashCollected = Double.parseDouble((String) data.get("cashCollected"));
            updateOp.set("cashCollected", cashCollected);
        }
        if(data.get("deliveredPackagesInfo") != null){
            List<PackageTypesDTO> deliveredPackagesInfo = (List<PackageTypesDTO>) data.get("deliveredPackagesInfo");
            updateOp.set("deliveredPackagesInfo",deliveredPackagesInfo);
            for(int i=0;i<deliveredPackagesInfo.size();i++){
                JSONObject jsonObject = new JSONObject((Map) deliveredPackagesInfo.get(i));
                totalDeliveries += Long.parseLong(jsonObject.get("count").toString());
            }
            updateOp.set("totalDeliveries",totalDeliveries);
            updateOp.set("totalPackages",userShift.getPickUpPackagesCount());
            updateOp.set("deliveredPackagesCount",totalDeliveries);
//            updateOp.set("totalReturns",(pickUpPackages.getCreturnsCount()+deliveredPackagesInfo.getCreturnsCount()));
        }
        if(data.get("shiftEndedBy") != null){
            updateOp.set("shiftEndedBy",data.get("shiftEndedBy").toString());
        }
        updateOp.set("status", UserShiftStatus.SHIFT_ENDED);
        updateOp.set("actualEndTime",calendar.getTime());
        updateOp.set("tripDistance",tripDistance);
        UpdateResult writeResult =  mongoTemplate.updateMulti(query, updateOp, UserShift.class);
        if(writeResult.getModifiedCount()>=1){
            userMongoDAO.endUserShift(userShift.getUserId(), tripDistance, totalDeliveries, cashCollected);
        }
        return userShiftDAO.findById(userShift.getId()).get();
    }


    public boolean updateUserShiftActive(String userShiftId){
        final Query query = new Query();
        query.addCriteria(where("id").is(userShiftId));
        Update updateOp = new Update();
        updateOp.set("shiftActive",true);
        UpdateResult writeResult =  mongoTemplate.updateMulti(query, updateOp, UserShift.class);
        if(writeResult.getModifiedCount()>=1){
            return true;
        }
        throw new BadRequestException("error while updating UserShift");
    }

    public Page<UserShift> search(JSONObject query,Pageable pageable) throws ParseException {
        Query q = searchQuery(query,pageable);
        long count = mongoTemplate.count(q, UserShift.class);
        List<UserShift> userShifts = mongoTemplate.find(q, UserShift.class);
        return new PageImpl<UserShift>(userShifts, pageable, count);
    }

    public long count(JSONObject query) throws ParseException {
        Query q = searchQuery(query, null);
        long count = mongoTemplate.count(q, UserShift.class);
        return count;
    }

    private Query searchQuery(JSONObject query, Pageable pageable) throws ParseException {
        Query q = new Query();
        List<Criteria> match = new ArrayList<>();
        Criteria criteria = new Criteria();
        if(query != null){
            if(query.containsKey("status") && !Strings.isNullOrEmpty(query.get("status").toString())) {
                match.add(where("status").is(query.get("status").toString()));
            }
            if(query.containsKey("startDate")) {
                match.add(where("shiftDate").gte(ServiceUtils.parseDate(query.get("startDate").toString(), false)));
            }
            if(query.containsKey("endDate")) {
                match.add(where("shiftDate").lte(ServiceUtils.parseDate(query.get("endDate").toString(), true)));
            }
            if(query.get("userId") != null && !Strings.isNullOrEmpty(query.get("userId").toString())){
                match.add(where("userId").is(query.get("userId").toString()));
            }
            if(query.get("clientId") != null && !Strings.isNullOrEmpty(query.get("clientId").toString())) {
                match.add(where("clientId").is(query.get("clientId").toString()));
            }
            if(query.get("siteId") != null && !Strings.isNullOrEmpty(query.get("siteId").toString())) {
                match.add(where("siteId").is(query.get("siteId").toString()));
            }
            if(query.get("templateId") != null && !Strings.isNullOrEmpty(query.get("templateId").toString())) {
                match.add(where("shiftTemplateId").is(query.get("templateId").toString()));
            }
        }
        if(pageable != null) {
            q.with(pageable);
        }
        if(match.size() > 0) {
            criteria.andOperator(match.toArray(new Criteria[match.size()]));
            q.addCriteria(criteria);
        }
        return q;
    }

    public boolean updateShiftStatus(String id, String status) {
        final Query query = new Query();
        query.addCriteria(where("id").is(id));
        Update updateOp = new Update();
        updateOp.set("status",status);
        UpdateResult writeResult =  mongoTemplate.updateMulti(query, updateOp, UserShift.class);
        return writeResult.getModifiedCount() ==1;
    }

    /**
     * Find shifts available at site for current calender date
     * @param siteId
     * @return
     */
    public List<UserShift> findShiftsAtSiteForToday(String siteId) {
        Calendar calendar = Calendar.getInstance();
        final Query query = new Query();
        query.addCriteria(Criteria.where("siteId").is(siteId));
        query.addCriteria(where("shiftDateStr").is(ServiceUtils.formatDate(calendar.getTime())));
        return mongoTemplate.find(query,UserShift.class);
    }

    public List<UserShift> findAllShiftsByUserId(List<String> userIds, String startDateStr, String endDateStr) throws ParseException {
        final Query query = new Query();
        query.addCriteria(where("userId").in(userIds));
        query.addCriteria(where("shiftDate").gte(ServiceUtils.parseDate(startDateStr,false))
                .lte(ServiceUtils.parseDate(endDateStr,true)));
        List<UserShift> userShiftList = mongoTemplate.find(query,UserShift.class);
        return userShiftList;
    }

    public boolean cancelShift(String shiftId, String cancellationReason) {
        final Query query = new Query();
        query.addCriteria(where("id").is(shiftId));
        Update updateOp = new Update();
        updateOp.set("status", UserShiftStatus.SHIFT_CANCELLED_BY_SUPERVISOR);
        updateOp.set("cancellationReason", cancellationReason);
        UpdateResult writeResult = mongoTemplate.updateMulti(query, updateOp, UserShift.class);
        return writeResult.getModifiedCount() ==1;
    }

    public boolean endShiftByAdmin(String shiftId, String alarmId) {
        Query query = new Query();
        query.addCriteria(where("id").is(shiftId));
        Update updateOp = new Update();
        updateOp.set("status", UserShiftStatus.SHIFT_CLOSED_BY_SUPERVISOR);
        updateOp.set("actualEndTime", new Date());
        UpdateResult writeResult =  mongoTemplate.updateMulti(query, updateOp, UserShift.class);
        return writeResult.getModifiedCount() ==1;
    }

    public boolean updateClientUserId(String shiftId, JSONObject clientUserIdInfo) {
        final Query query = new Query();
        query.addCriteria(where("_id").is(shiftId));
        Update updateOp = new Update();
        updateOp.set("clientUserIdInfo",clientUserIdInfo);
        UpdateResult updateResult = mongoTemplate.updateMulti(query,updateOp,UserShift.class);
        return updateResult.getModifiedCount() == 1;
    }

    private static void setTimeToBeginningOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    public List<Document> aggregateUserShiftData() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH,
                calendar.getActualMinimum(Calendar.DAY_OF_MONTH));
        setTimeToBeginningOfDay(calendar);
        Date date = calendar.getTime();
        Aggregation aggregation = newAggregation(
                match(Criteria.where("actualEndTime").gt(date)),
                group().sum("totalDeliveries").as("totalDeliveries").sum("tripDistance").as("tripDistance"));
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, UserShift.class,Document.class);
        List<Document> result = results.getMappedResults();
        return  result;
    }

    /**
     * Get summary of the shift available for given date. This
     * @param shiftDate
     * @return
     */

    public JSONObject getShiftsSummary(String shiftDate,String siteId) {
        long counter[] = new long[5];
        Set<String> siteIds = new HashSet<>();
        User currentUser = sessionManager.getCurrentUser();
        final Query query = new Query();
        query.addCriteria(where("shiftDateStr").is(shiftDate));
        if(currentUser.getRole() != Role.SUPER_USER.getValue()) {
            //List<Site> supervisorSites = userMongoDAO.findSitesForSupervisor(sessionManager.getCurrentUser().getId());
                List<SiteAdmins> siteAdminsList = siteAdminsDAO.findAllByAdminId(currentUser.getId());
                for (SiteAdmins siteAdmin:siteAdminsList){
                    siteIds.add(siteAdmin.getSiteId());
                }
        }
        if(!Strings.isNullOrEmpty(siteId)){
            siteIds = new HashSet<>();
            siteIds.add(siteId);
        }
        if(siteIds.size() > 0){
            query.addCriteria(where("siteId").in(siteIds));
        }
        List<UserShift> userShiftList = mongoTemplate.find(query,UserShift.class);
        userShiftList.stream().forEach(userShift -> {
            if(userShift.getStatus() == UserShiftStatus.SHIFT_ENDED){
                counter[0]++;
            }
            if(userShift.getStatus() == UserShiftStatus.SHIFT_CANCELLED_BY_SUPERVISOR){
                counter[1]++;
            }
            if(userShift.getStatus() == UserShiftStatus.SHIFT_IN_PROGRESS){
                counter[2]++;
//                counter[4]++;
            }
            if(userShift.getStatus() == UserShiftStatus.INIT){
                counter[3]++;
            }
            if(userShift.getStatus() == UserShiftStatus.ATTENDANCE_MARKED){
                counter[4]++;
            }
        });
        JSONObject shiftsCount = new JSONObject();
        shiftsCount.put("shiftsEnded",counter[0]);
        shiftsCount.put("shiftsCancelled",counter[1]);
        shiftsCount.put("shiftsInProgress",counter[2]);
        shiftsCount.put("shiftsAvailable",counter[3]);
        shiftsCount.put("shiftsAttendanceMarked",counter[4]);
        shiftsCount.put("shiftsCount",userShiftList.size());
        return shiftsCount;
    }

    public Query tripsQuery(JSONObject data) throws ParseException {
        final Query query = new Query();
        query.addCriteria(where("status").is(UserShiftStatus.SHIFT_ENDED));
        if(data.containsKey("startDate") && data.containsKey("endDate")) {
            query.addCriteria(where("shiftDate").gte(ServiceUtils.parseDate(data.get("startDate").toString(), false))
                    .lte(ServiceUtils.parseDate(data.get("endDate").toString(), true)));
        }
        if(data.containsKey("userId") && !Strings.isNullOrEmpty(data.get("userId").toString())){
            query.addCriteria(where("userId").is(data.get("userId").toString()));
        }else{
            query.addCriteria(where("userId").is(sessionManager.getCurrentUser().getId()));
        }
        if(data.containsKey("siteId") && !Strings.isNullOrEmpty(data.get("siteId").toString())){
            query.addCriteria(where("siteId").is(data.get("siteId").toString()));
        }
        query.with(new Sort(Sort.Direction.DESC,"shiftDate"));
        return query;
    }

    public UserShift updatePackagesInfo(String shiftId, JSONObject packagesInfo) {
        long pickUpPackagesCount = 0;
        final Query query = new Query();
        query.addCriteria(where("id").is(shiftId));
        Update update = new Update();
        if(packagesInfo.get("pickUpPackagesInfo") != null){
            List<PackageTypesDTO> pickUpPackagesInfo = (List<PackageTypesDTO>) packagesInfo.get("pickUpPackagesInfo");
            for(int i=0;i<pickUpPackagesInfo.size();i++){
                JSONObject jsonObject = new JSONObject((Map) pickUpPackagesInfo.get(i));
                pickUpPackagesCount += Long.parseLong(jsonObject.get("count").toString());
            }
            update.set("pickUpPackagesInfo",pickUpPackagesInfo);
            /* Map<String,Integer> pickUpPackagesInfo = new HashMap<>();
            Object packagesList = packagesInfo.get("pickUpPackagesInfo");
            if( packagesList instanceof  List){
                List<JSONObject> packagesData = (List<JSONObject>) packagesInfo.get("pickUpPackagesInfo");
                for(int i=0;i<packagesData.size();i++){
                    Map<String,String> jsonObject = packagesData.get(i);
                    pickUpPackagesInfo.put(jsonObject.get("type"), Integer.valueOf(String.valueOf(jsonObject.get("count"))));
                }
            } else if (packagesList instanceof  Map){
                pickUpPackagesInfo = (Map<String, Integer>) packagesInfo.get("pickUpPackagesInfo");
            }*/
            update.set("pickUpPackagesCount",pickUpPackagesCount);
        }
        UpdateResult updateResult = mongoTemplate.updateMulti(query,update,UserShift.class);
        if(updateResult.getModifiedCount() == 1){
            return userShiftDAO.findById(shiftId).get();
        }
        throw  new BadRequestException("Error in updating packages");
    }

    /**
     * Find the clientUserIds grouped by userId
     * @param userIds
     * @return
     */
    public Map<String, String> findShiftCountsForUsers(List<String> userIds) {
        Map<String, String> userShiftCount = new HashMap<>();
        List<Criteria> match = new ArrayList<>();
        Criteria criteria = new Criteria();
        if(userIds != null && userIds.size() > 0){
            match.add(where("userId").in(userIds));
        }
        match.add(Criteria.where("status").in(UserShiftStatus.INIT, UserShiftStatus.SHIFT_IN_PROGRESS,UserShiftStatus.ATTENDANCE_MARKED));
        match.add(Criteria.where("shiftDateStr").is(ServiceUtils.formatDate(new Date())));
        criteria.andOperator(match.toArray(new Criteria[match.size()]));
        Query query = new Query(criteria);
        List<UserShift> userShiftList = mongoTemplate.find(query,UserShift.class);
        Aggregation agg = newAggregation(
                match(criteria),
                group("userId")
                        .count().as("shiftsCount"));
        AggregationResults<Document> groupResults
                = mongoTemplate.aggregate(agg, UserShift.class, Document.class);
        List<Document> results = groupResults.getMappedResults();
        results.stream().forEach(result -> {
            userShiftCount.put(result.getString("_id"), result.get("shiftsCount").toString());
        });
        return userShiftCount;
    }

    public List<UserShift> getShiftsToUpdate(JSONObject query) throws ParseException {
        query.put("status",UserShiftStatus.INIT);
        Query q = searchQuery(query, null);
        List<UserShift> userShiftList =  mongoTemplate.find(q,UserShift.class);
        return userShiftList;
    }

    public void updateShift(List<String> shiftIds,String shiftId, ShiftTemplate shiftTemplate, Date shiftDate, int role) {
        Query query = new Query();
        if(shiftIds != null && shiftIds.size() > 0){
            query.addCriteria(where("id").in(shiftIds));
        }
        if(!Strings.isNullOrEmpty(shiftId)){
            query.addCriteria(where("id").is(shiftId));
        }
        Update update = new Update();
        if(shiftTemplate != null){
            update.set("shiftTemplateId",shiftTemplate.getId());
            update.set("startTime",shiftTemplate.getStartTime());
            update.set("endTime",shiftTemplate.getEndTime());
            Calendar date = Calendar.getInstance();
            date.setTime(shiftDate);
            date.set(Calendar.HOUR, shiftTemplate.getStartTime().getHours());
            date.set(Calendar.MINUTE, shiftTemplate.getStartTime().getMinutes());
            update.set("expectedStartTime",date.getTime());
            date.set(Calendar.HOUR, shiftTemplate.getEndTime().getHours());
            date.set(Calendar.MINUTE, shiftTemplate.getEndTime().getMinutes());
            update.set("expectedEndTime",date.getTime());
        }
        if(role > 0){
            update.set("userRole",role);
        }
        mongoTemplate.updateMulti(query,update,UserShift.class);
    }

    public void updateShift(String shiftId, String field,Object value) {
        Query query = new Query();
        query.addCriteria(where("id").is(shiftId));
        Update update = new Update();
        update.set(field,value);
        mongoTemplate.updateMulti(query,update,UserShift.class);
    }

    /* Find the first shift sortedByCreatedDate and use that to display in profile */

    public UserShift findUserShift(String userId, String shiftDate) {
        final Query query = new Query();
        query.addCriteria(where("userId").is(userId));
        query.addCriteria(where("shiftDateStr").is(shiftDate));
        query.with(new Sort(Sort.Direction.DESC,"createdAt"));
        query.fields().include("shiftTemplateId");
        return mongoTemplate.findOne(query,UserShift.class);
    }
    public  void suspendUserShift(String userId){
        final Query query=new Query();
        query.addCriteria(where("userId").is(userId));
        query.addCriteria(where("shiftDate").gte(new Date()));
        query.addCriteria(where("status").is(UserShiftStatus.INIT.toString()));
        Update updateOp = new Update();
        updateOp.set("status",UserShiftStatus.SHIFT_SUSPENDED );
        mongoTemplate.updateMulti(query,updateOp,UserShift.class);
    }

}
