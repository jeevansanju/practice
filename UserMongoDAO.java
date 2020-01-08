package com.wizzard.dao.impl;


import com.mongodb.client.result.UpdateResult;
import com.wizzard.controller.UserShiftController;
import com.wizzard.dao.SiteAdminsDAO;
import com.wizzard.dao.UserActivationDAO;
import com.wizzard.exception.BadRequestException;
import com.wizzard.model.Role;
import com.wizzard.model.Site;
import com.wizzard.model.User;
import com.wizzard.dao.UserDAO;
import com.wizzard.model.*;
import com.wizzard.services.SessionManager;
import com.wizzard.services.UserShiftManager;
import com.wizzard.util.ServiceUtils;
import org.joda.time.DateTime;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

import java.util.*;

import static com.wizzard.model.User.COLLECTION_NAME;
import static org.springframework.data.mongodb.core.query.Criteria.where;
@Repository
public class UserMongoDAO {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private UserActivationDAO userActivationDAO;

    @Autowired
    private ServiceUtils serviceUtils;

    @Autowired
    private SiteAdminsDAO siteAdminsDAO;

    @Autowired
    private SiteMongoDAO siteMongoDAO;


    public boolean updateUser(String userId, double userTotalDistance, double userTotalDeliveries, double trips){
        final Query query = new Query();
        query.addCriteria(where("_id").is(userId));
        Update updateOp = new Update();
        updateOp.set("totalDistance",(long)userTotalDistance);
        updateOp.set("totalDeliveries",(long)userTotalDeliveries);
        updateOp.set("totalTrips",(long)trips);
        UpdateResult writeResult =  mongoTemplate.updateMulti(query, updateOp, User.class);
        return writeResult.getModifiedCount() == 1;
    }

    /**
     * Update the shift related info on to User
     * @param userId
     * @param tripDistance
     * @param tripDeliveries
     * @return
     */
    public boolean endUserShift(String userId, double tripDistance, double tripDeliveries, double cashCollected){
        final Query query = new Query();
        query.addCriteria(where("_id").is(userId));
        Update updateOp = new Update();
        updateOp.inc("totalDistance",tripDistance);
        updateOp.inc("totalDeliveries",tripDeliveries);
        updateOp.inc("cashCollected", cashCollected);
        updateOp.inc("totalTrips",1);
        UpdateResult writeResult =  mongoTemplate.updateMulti(query, updateOp, User.class);
        return writeResult.getModifiedCount() == 1;
    }

    public List<User> getSupervisors() {
        Criteria criteria = new Criteria();
        criteria.orOperator(Criteria.where("deleted").is(false), Criteria.where("deleted").exists(false));
        Query query = new Query(criteria);
        query.addCriteria(where("role").gte(Role.SITE_SUPERVISOR.getValue()));
        return mongoTemplate.find(query,User.class);
    }

    public Query getUsersForSupervisorsQuery(JSONObject data) {
        User currentUser = sessionManager.getCurrentUser();
        Query query = new Query();
        List<Criteria> criterias = new ArrayList<>();
        criterias.add(new Criteria().orOperator(where("deleted").is(false), where("deleted").exists(false)));
        query.addCriteria(where("status").is(UserStatus.ACTIVATED));
        if(currentUser.getRole() != Role.SUPER_USER.getValue()){
            List<String> siteIds = new ArrayList<>();
            List<SiteAdmins> siteAdminsList = siteAdminsDAO.findAllByAdminId(currentUser.getId());
            siteAdminsList.stream().forEach(siteAdmins -> {
               siteIds.add(siteAdmins.getSiteId());
            });
            Set<String> supervisors = siteMongoDAO.findSiteAdmins(siteIds);
            /* List<Site> sites = findSitesForSupervisor(currentUser.getId());
            sites.stream().forEach(site -> {
                siteIds.add(site.getId());
                if(site.getSiteAdministratorId() != null){
                    supervisors.addAll(site.getSiteAdministratorId());
                }
            });*/
            if(siteIds.size() > 0){
                criterias.add(new Criteria().orOperator(where("siteIds").in(siteIds), where("_id").in(supervisors)));
            }
        }
        if(data.containsKey("userName")){
            criterias.add(new Criteria().orOperator(where("userName").regex((String) data.get("userName"), "i"),
                    where("fullName").regex((String) data.get("userName"), "i"),
                    where("phoneNumber").regex((String) data.get("userName"), "i")));
        }
        if(data.get("role") != null){
          query.addCriteria(where("role").is(data.get("role")));
        }
        query.addCriteria(new Criteria().andOperator(criterias.toArray(new Criteria[criterias.size()])));
        query.with(new Sort(Sort.Direction.ASC,"fullName"));
        query.collation(Collation.of("en").strength(Collation.ComparisonLevel.secondary()));
        return query;
    }

    public User resetUserPassword(String userName, String password, ResetPassword data) {
        //To update user password
        final Query query = new Query();
        query.addCriteria(where("userName").is(userName));
        Update updateOp = new Update();
        updateOp.set("password",encoder.encode(password));
        UpdateResult updateResult = mongoTemplate.updateMulti(query,updateOp,User.class);
        if(updateResult.getModifiedCount() == 1){
            //To make code inactive
            final Query query1 = new Query();
            query1.addCriteria(where("_id").is(data.getId()));
            Update updateOp1 = new Update();
            updateOp1.set("isActive",false);
            mongoTemplate.updateMulti(query1,updateOp1,ResetPassword.class);
        }
        return userDAO.findByUserName(userName).get();
    }

    public Page<User> getUsersForSupervisors(JSONObject data, Pageable pageable) {
        Query query = getUsersForSupervisorsQuery(data);
        long total = mongoTemplate.count(query,User.class);
        query.with(pageable);
        List<User> users = mongoTemplate.find(query,User.class);
        return new PageImpl<>(users, pageable, total);
    }

    public UserStatus getUserStatus(String userId) {
        User user = userDAO.findById(userId).get();
        return user.getStatus();
    }

    public long getUsersForSupervisorsCount(JSONObject data) {
        Query query = getUsersForSupervisorsQuery(data);
        return mongoTemplate.count(query,User.class);
    }

    public void deleteUser(String userId) {
        final Query query=new Query();
        query.addCriteria(where("_id").is(userId));
        mongoTemplate.find(query,User.class);
        Update updateOp = new Update();
        updateOp.set("deleted",true);
        mongoTemplate.updateMulti(query,updateOp,User.class);
    }

    public User changeUserPassword(String password, String id) {
        final Query query = new Query();
        query.addCriteria(where("_id").is(id));
        Update updateOp = new Update();
        updateOp.set("password",encoder.encode(password));
        mongoTemplate.updateMulti(query,updateOp,User.class);
        return userDAO.findById(id).get();
    }

    private Query createPendingUsersQuery(JSONObject data){
        Criteria criteria = new Criteria();
        criteria.orOperator(Criteria.where("deleted").is(false), Criteria.where("deleted").exists(false));
        Query query = new Query(criteria);
        query.addCriteria(where("status").is(UserStatus.ACTIVATION_PENDING));
        if(data.get("siteId") != null){
            query.addCriteria(where("siteIds").is(data.get("siteId")));
        }
        if(data.get("name") != null){
            query.addCriteria(where("fullName").regex((String) data.get("name"), "i"));
        }
        if(data.get("phoneNumber") != null){
            query.addCriteria(where("phoneNumber").regex((String) data.get("phoneNumber"), "i"));
        }
        if(data.get("role") != null){
            query.addCriteria(where("role").is(Integer.parseInt(data.get("role").toString())));
        }
        query.with(new Sort(Sort.Direction.DESC,"createdAt"));
        return query;
    }

    public Page<User> getActivationPendingUsers(JSONObject data) {
        Query query = createPendingUsersQuery(data);
        long total = mongoTemplate.count(query,User.class);
        PageRequest pageable = PageRequest.of(0,10);
        if(data.get("size") != null && data.get("page") != null){
            int page = (int) data.get("page")-1;
            pageable = PageRequest.of(page,(int) data.get("size"));
        }
        query.with(pageable);
        List<User> users = find(query);
        return new PageImpl<>(users, pageable, total);
    }

    private List<User> find(Query query){
        List<User> users = mongoTemplate.find(query,User.class);
        users.stream().forEach(user -> {
            if(user.getSiteIds() != null){
                Site site = siteMongoDAO.findByUserSiteIds(user.getSiteIds());
                if(site != null){
                    user.getAttributes().put("siteName",site.getName());
                    user.getAttributes().put("siteCode",site.getSiteCode());
                }
            }
        });
        return users;
    }

    public long getPendingUsersCount(JSONObject data) {
        Query query = createPendingUsersQuery(data);
        return mongoTemplate.count(query,User.class);
    }

    public boolean activateUser(String userId, String remarks, int role, UserStatus status, String site) {
        if(userDAO.findById(userId).get() == null){
            throw new BadRequestException("User not found");
        }
        List<String> siteIds = new ArrayList<>();
        siteIds.add(site);
        String activatedBy = sessionManager.getCurrentUser().getId();
        final Query query = new Query();
        query.addCriteria(where("_id").is(userId));
        Update updateOp = new Update();
        updateOp.set("role",role);
        updateOp.set("status",status);
        updateOp.set("siteIds",siteIds);
        UpdateResult updateResult = mongoTemplate.updateMulti(query,updateOp, User.class);
        if(updateResult.getModifiedCount() == 1){
            /*Create user activation data*/
            UserActivation data = new UserActivation();
            data.setActivatedBy(activatedBy);
            data.setActivatedOn(new DateTime());
            data.setActivationRemarks(remarks);
            data.setUserId(userId);
            data.getAttributes().put("activatedBy",userDAO.findById(activatedBy).get().getFullName());
            userActivationDAO.save(data);
            return updateResult.getModifiedCount() == 1;
        }
        return false;
    }

    public boolean unDeleteUser(String userId) {
        if(userDAO.findById(userId).get() == null){
            throw new BadRequestException("User not found");
        }
        if(sessionManager.getCurrentUser().getRole() == Role.SUPER_USER.getValue()){
            final Query query = new Query();
            query.addCriteria(where("_id").is(userId));
            Update updateOp = new Update();
            updateOp.set("deleted",false);
            updateOp.set("status",UserStatus.ACTIVATION_PENDING);
            UpdateResult updateResult = mongoTemplate.updateMulti(query,updateOp, User.class);
            return updateResult.getModifiedCount() == 1;
        }else{
            throw new BadRequestException("Error");
        }
    }

    public Page<User> getDeletedUsers(JSONObject data) {
        Query query =  serviceUtils.createGetQuery(true);
        long count = mongoTemplate.count(query,User.class);
        PageRequest pageable = PageRequest.of(0,10);
        if(data.get("size") != null && data.get("page") != null){
            int page = (int) data.get("page")-1;
            pageable = PageRequest.of(page,(int) data.get("size"));
        }
        query.with(pageable);
        List<User> users = mongoTemplate.find(query,User.class);
        return new PageImpl<>(users, pageable, count);
    }

    public List<User> findUsersByIds(Set<String> siteSupervisorIds) {
        Criteria criteria = new Criteria();
        criteria.orOperator(Criteria.where("deleted").is(false), Criteria.where("deleted").exists(false));
        Query query = new Query(criteria);
        query.addCriteria(where("id").in(siteSupervisorIds));
        query.fields().include("phoneNumber");
        query.fields().include("fullName");
        return mongoTemplate.find(query,User.class);
    }

    public User findUser(String phoneNumber) {
        final Query query = new Query();
        query.addCriteria(where("userName").is(phoneNumber));
        return mongoTemplate.findOne(query,User.class,COLLECTION_NAME);
    }

    public boolean rejectUser(String userId, String reason) {
        if(userDAO.findById(userId).get() == null){
            throw new BadRequestException("User not found");
        }
        final Query query = new Query();
        query.addCriteria(where("_id").is(userId));
        Update updateOp = new Update();
        updateOp.set("status",UserStatus.REJECTED);
        UpdateResult updateResult = mongoTemplate.updateMulti(query,updateOp, User.class);
        if(updateResult.getModifiedCount() == 1){
            UserActivation data = new UserActivation();
            data.setUserId(userId);
            data.setRejectionReason(reason);
            data.setRejectedBy(sessionManager.getCurrentUser().getId());
            data.setRejectedOn(new DateTime());
            userActivationDAO.save(data);
            return true;
        }
        return false;
    }

    public long getDeletedUsersCount() {
        Query query = new Query();
        query.addCriteria(where("deleted").is(true));
        return mongoTemplate.count(query,User.class);
    }

    public long getRejectedUsersCount() {
        final Query query = new Query();
        query.addCriteria(where("status").is(UserStatus.REJECTED));
        return mongoTemplate.count(query,User.class);
    }

    public List<User> getRejectedUsers(PageRequest pageable) {
        Query query = new Query();
        query.addCriteria(where("status").is(UserStatus.REJECTED));
        query.with(pageable);
        List<User> users = find(query);
        return users;
    }

    public void updateSiteAdmins(User user) {
        final Query query = new Query();
        query.addCriteria(where("adminId").is(user.getId()));
        mongoTemplate.findAllAndRemove(query,SiteAdmins.class);
        createSiteAdmins(user.getId(),user.getSiteIds());
    }

    public void createSiteAdmins(String userId, List<String> siteIds) {
        for (String siteId:siteIds){
            SiteAdmins siteAdmins = new SiteAdmins();
            siteAdmins.setAdminId(userId);
            siteAdmins.setSiteId(siteId);
            siteAdminsDAO.save(siteAdmins);
        }
    }

    public List<User> getDeviceUsers(Set<String> userIds, String userName) {
        Query query =  serviceUtils.createGetQuery(false);
        query.addCriteria(where("id").in(userIds));
        query.addCriteria(where("fullName").regex(userName, "i"));
        return mongoTemplate.find(query,User.class);
    }

    /**
     * Find userIds for matching usernames
     * @param userName
     * @return
     */
    public List<String> searchUserIds(String userName){
        Query query =  serviceUtils.createGetQuery(false);
        query.addCriteria(where("fullName").regex(userName, "i"));
        query.fields().include("id");
        List<User> users = mongoTemplate.find(query,User.class);
        List<String> userIds = new ArrayList<>();
        users.stream().forEach(user -> {
            userIds.add(user.getId());
        });
        return userIds;
    }

    public boolean reactivateUser(String userId) {
        final Query query = new Query();
        query.addCriteria(where("_id").is(userId));
        Update updateOp = new Update();
        updateOp.set("status",UserStatus.ACTIVATION_PENDING);
        UpdateResult updateResult = mongoTemplate.updateMulti(query,updateOp, User.class);
        if(updateResult.getModifiedCount() == 1){
            return true;
        }
        return false;
    }
}
