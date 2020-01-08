package com.wizzard.services;


import com.wizzard.BaseTest;
import com.wizzard.dao.*;
import com.wizzard.dto.UserLoginDTO;
import com.wizzard.model.*;
import com.wizzard.util.ServiceUtils;
import jdk.nashorn.internal.objects.annotations.Where;
import org.joda.time.DateTime;
import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;


import java.text.ParseException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.springframework.data.mongodb.core.query.Criteria.where;


@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest
public class UserManagerTest extends BaseTest {

    @Autowired
    private ServiceUtils serviceUtils;

    @Autowired
    private ShiftTemplateDAO shiftTemplateDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private UserShiftDAO userShiftDAO;

    @Autowired
    UserManager userManager;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private SiteDAO siteDAO;

    @Autowired
    private SiteAdminsDAO siteAdminsDAO;

    @Autowired
    private ClientDAO clientDAO;

    @Before
    @After
    public void cleanup(){
        userShiftDAO.deleteAll();
        siteDAO.deleteAll();
        siteAdminsDAO.deleteAll();
        super.setup(userDAO);
        User user = userDAO.findByUserName("john").get();
        sessionManager.setCurrentUser(user);
    }

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();



//    @Test
//    public void testuserLoggedIn() {
//        UserShift userShift=new UserShift();
//        Calendar calaender=Calendar.getInstance();
//        userShift.setShiftDate(calaender.getTime());
//        userShift.setReportingTime(calaender.getTime());
//        userShift.setSiteId("456");
//        userShift.setTotalPackages(25);
//        userShift.setTotalDeliveries(13);
//        userShift.setUserId(sessionManager.getCurrentUser().getId());
//        userShift.setShiftDateStr(ServiceUtils.formatDate(calaender.getTime()));
//        userShiftDAO.save(userShift);
//        UserShift userShift1=new UserShift();
//        userShift1.setShiftDate(calaender.getTime());
//        userShift1.setReportingTime(calaender.getTime());
//        userShift1.setSiteId("456");
//        userShift1.setTotalPackages(35);
//        userShift1.setTotalDeliveries(20);
//        userShift1.setUserId(sessionManager.getCurrentUser().getId());
//        userShift1.setShiftDateStr(ServiceUtils.formatDate(calaender.getTime()));
//        userShiftDAO.save(userShift1);
//        UserLoginDTO userLoginDTO = userManager.getLoggedUserDetails();
////        assertEquals(UserShiftStatus.ATTENDANCE_MARKED,userShift.getStatus());
//        assertEquals(13,userLoginDTO.getOtherShifts().get(0).getTotalDeliveries());
//        assertEquals(35,userLoginDTO.getOtherShifts().get(1).getTotalPackages());
//    }

    @Test
    public void testGetUsers(){
        for(int i=0;i<10;i++){
            User user = new User();
            if(i%2 == 0){
                user.setStatus(UserStatus.ACTIVATION_PENDING);
            }
            user.setDateJoined(new DateTime());
            userDAO.save(user);
        }
        JSONObject data = new JSONObject();
        data.put("size",10);
        data.put("page",1);
        Page<User> page = userManager.getActivationPendingUsers(data);
        assertEquals(5,page.getTotalElements());

        for(int i=0;i<15;i++){
            User user = new User();
            if(i%2 == 0){
                user.setStatus(UserStatus.ACTIVATION_PENDING);
            }
            user.setDateJoined(new DateTime());
            userDAO.save(user);
        }
        data = new JSONObject();
        data.put("size",10);
        data.put("page",1);
        page = userManager.getActivationPendingUsers(data);
        List<User> users = page.getContent();
        assertEquals(10,users.size());
        data = new JSONObject();
        data.put("size",10);
        data.put("page",2);
        page = userManager.getActivationPendingUsers(data);
        users = page.getContent();
        assertEquals(3,users.size());
    }

    @Test
    public void testGetAllUsers(){
        User supervisor1 = new User();
        supervisor1.setFullName("Kalyani kandula");
        supervisor1.setRole(Role.SITE_SUPERVISOR.getValue());
        supervisor1.setStatus(UserStatus.ACTIVATED);
        supervisor1 = userDAO.save(supervisor1);
        sessionManager.setCurrentUser(supervisor1);
        User supervisor2 = new User();
        supervisor2.setFullName("Kalyani94");
        supervisor2.setRole(Role.SITE_SUPERVISOR.getValue());
        supervisor2.setStatus(UserStatus.ACTIVATED);
        supervisor2 = userDAO.save(supervisor2);
        Site site1 = new Site();
        site1.setName("madhapur");
//        site1.setSiteAdministratorId(siteAdminIds);
        site1 = siteDAO.save(site1);
        SiteAdmins siteAdmin1 = new SiteAdmins();
        siteAdmin1.setSiteId(site1.getId());
        siteAdmin1.setAdminId(supervisor1.getId());
        siteAdminsDAO.save(siteAdmin1);
        Site site2 = new Site();
        site2.setName("Banglore");
//        site2.setSiteAdministratorId(siteAdminIds);
        site2 = siteDAO.save(site2);
        SiteAdmins siteAdmin2 = new SiteAdmins();
        siteAdmin2.setSiteId(site2.getId());
        siteAdmin2.setAdminId(supervisor1.getId());
        siteAdminsDAO.save(siteAdmin2);
        SiteAdmins siteAdmin3 = new SiteAdmins();
        siteAdmin3.setSiteId(site2.getId());
        siteAdmin3.setAdminId(supervisor2.getId());
        siteAdminsDAO.save(siteAdmin3);
        for(int i=0;i<30;i++){
            User user = new User();
            user.setStatus(UserStatus.ACTIVATED);
            if(i%2 == 0){
                List<String> siteIds = new ArrayList<>();
                siteIds.add(site1.getId());
                user.setRole(Role.DRIVER.getValue());
                user.setSiteIds(siteIds);
                user.setFullName("kalyani"+i);
            }else{
                List<String> siteIds = new ArrayList<>();
                siteIds.add(site2.getId());
                user.setRole(Role.DRIVER_AND_ASSOCIATE.getValue());
                user.setSiteIds(siteIds);
                user.setFullName("divya"+i);
            }
            userDAO.save(user);
        }
        PageRequest pageable = PageRequest.of(0,Integer.MAX_VALUE);
        Page<User> page = userManager.getUsersForSupervisors(new JSONObject(),pageable);
        List<User> users = page.getContent();
        assertEquals(32,users.size());
        pageable = PageRequest.of(1,10);
        page = userManager.getUsersForSupervisors(new JSONObject(), pageable);
        users = page.getContent();
        assertEquals(10,users.size());
        pageable = PageRequest.of(2,10);
        page = userManager.getUsersForSupervisors(new JSONObject(),pageable);
        users = page.getContent();
        assertEquals(10,users.size());
        pageable = PageRequest.of(3,10);
        page = userManager.getUsersForSupervisors(new JSONObject(), pageable);
        users = page.getContent();
        assertEquals(2,users.size());
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("userName","y");
        page = userManager.getUsersForSupervisors(jsonObject, new PageRequest(0,Integer.MAX_VALUE));
        users = page.getContent();
        assertEquals(32,users.size());
        jsonObject = new JSONObject();
        jsonObject.put("role",Role.DRIVER_AND_ASSOCIATE.getValue());
        page = userManager.getUsersForSupervisors(jsonObject, new PageRequest(0,Integer.MAX_VALUE));
        users = page.getContent();
        assertEquals(15,users.size());
        jsonObject = new JSONObject();
        jsonObject.put("role",Role.DRIVER_AND_ASSOCIATE.getValue());
        pageable = PageRequest.of(1,10);
        page = userManager.getUsersForSupervisors(jsonObject, pageable);
        users = page.getContent();
        assertEquals(5,users.size());
    }

    @Test
    public void testGetLoggedUserDetailsWithOutShifts(){
        User supervisor1 = new User();
        supervisor1.setUserName("Kalyani kandula");
        supervisor1.setRole(Role.SITE_SUPERVISOR.getValue());
        supervisor1.setPhoneNumber("7788009911");
        supervisor1.setFullName("kalyani");
        supervisor1 = userDAO.save(supervisor1);
        User supervisor2 = new User();
        supervisor2.setUserName("Divya Kandula");
        supervisor2.setPhoneNumber("8989898989");
        supervisor2.setRole(Role.SITE_SUPERVISOR.getValue());
        supervisor2.setFullName("Divya Kandula");
        supervisor2 = userDAO.save(supervisor2);
        List<String> siteAdministrator = new ArrayList<>();
        siteAdministrator.add(supervisor1.getId());
        siteAdministrator.add(supervisor2.getId());
        Site site1 = new Site();
        site1.setName("Amazon-Hyderabad");
//        site1.setSiteAdministratorId(siteAdministrator);
        site1 = siteDAO.save(site1);
        SiteAdmins siteAdmin1 = new SiteAdmins();
        siteAdmin1.setSiteId(site1.getId());
        siteAdmin1.setAdminId(supervisor1.getId());
        siteAdminsDAO.save(siteAdmin1);
        SiteAdmins siteAdmin2 = new SiteAdmins();
        siteAdmin2.setSiteId(site1.getId());
        siteAdmin2.setAdminId(supervisor2.getId());
        siteAdminsDAO.save(siteAdmin2);
        User supervisor3 = new User();
        supervisor3.setUserName("John");
        supervisor3.setRole(Role.SITE_SUPERVISOR.getValue());
        supervisor3.setPhoneNumber("9999000011");
        supervisor3.setFullName("John");
        supervisor3 = userDAO.save(supervisor3);
        siteAdministrator = new ArrayList<>();
        siteAdministrator.add(supervisor3.getId());
        siteAdministrator.add(supervisor1.getId());
        Site site2 = new Site();
        site2.setName("Myntra-Banglore");
//        site2.setSiteAdministratorId(siteAdministrator);
        site2 = siteDAO.save(site2);
        SiteAdmins siteAdmin3 = new SiteAdmins();
        siteAdmin3.setSiteId(site2.getId());
        siteAdmin3.setAdminId(supervisor1.getId());
        siteAdminsDAO.save(siteAdmin3);
        SiteAdmins siteAdmin4 = new SiteAdmins();
        siteAdmin4.setSiteId(site2.getId());
        siteAdmin4.setAdminId(supervisor3.getId());
        siteAdminsDAO.save(siteAdmin4);
        List<String> siteIds = new ArrayList<>();
        siteIds.add(site1.getId());
        siteIds.add(site2.getId());
        User user = new User();
        user.setUserName("kalyanni@94");
        user.setFullName("Kalyani");
        user.setStatus(UserStatus.ACTIVATED);
        user.setSiteIds(siteIds);
        user = userDAO.save(user);
        sessionManager.setCurrentUser(user);
        UserLoginDTO userLoginDTO = userManager.getLoggedUserDetails();
        assertNotNull(userLoginDTO.getSiteSupervisorsInfo());
        assertEquals(0,userLoginDTO.getTotalDeliveries());
        assertNull(userLoginDTO.getCompletedShifts());
    }

    @Test
    public void testAddUser(){
        Site site1 = new Site();
        site1.setName("Amazon-Hyderabad");
        site1 = siteDAO.save(site1);
        Site site2 = new Site();
        site2.setName("Myntra-Banglore");
        site2 = siteDAO.save(site2);
        List<String> siteIds = new ArrayList<>();
        siteIds.add(site1.getId());
        siteIds.add(site2.getId());
        User user = new User();
        user.setFullName("kalyani kandula");
        user.setUserName("9911662200");
        user.setPhoneNumber("8900000000");
        user.setPassword("123456");
        user.setRole(Role.SITE_SUPERVISOR.getValue());
        user.setPrimarySite(site2.getId());
        user.setSiteIds(siteIds);
        userManager.addUser(user);
        List<SiteAdmins> siteAdminsList = siteAdminsDAO.findAllByAdminId(user.getId());
        assertEquals(2,siteAdminsList.size());
        User user1 = new User();
        user1.setFullName("kalyani");
        user1.setUserName("9911667722");
        user1.setPhoneNumber("8900111111");
        user1.setPassword("123456");
        user1.setRole(Role.DRIVER_AND_ASSOCIATE.getValue());
        user1.setPrimarySite(site2.getId());
        user1.setSiteIds(siteIds);
        userManager.addUser(user1);
        siteAdminsList = siteAdminsDAO.findAllByAdminId(user1.getId());
        assertEquals(0,siteAdminsList.size());
    }

    @Test
    public void testUpdateUser(){
        List<String> siteIds = new ArrayList<>();
        siteIds.add("121");
        siteIds.add("111");
        User user = new User();
        user.setFullName("kalyani kandula");
        user.setUserName("9911662200");
        user.setPhoneNumber("8900000000");
        user.setPassword("123456");
        user.setRole(Role.SITE_SUPERVISOR.getValue());
        user.setPrimarySite("132");
        user.setSiteIds(siteIds);
        user = userDAO.save(user);
        for(int i=0;i<user.getSiteIds().size();i++){
            SiteAdmins siteAdmins = new SiteAdmins();
            siteAdmins.setSiteId(user.getSiteIds().get(i));
            siteAdmins.setAdminId(user.getId());
            siteAdminsDAO.save(siteAdmins);
        }
        List<SiteAdmins> siteAdminsList = siteAdminsDAO.findAllByAdminId(user.getId());
        assertEquals(2,siteAdminsList.size());
        siteIds = new ArrayList<>();
        siteIds.add("121");
        siteIds.add("111");
        siteIds.add("909");
        user.setFullName("Kalyani");
        user.setSiteIds(siteIds);
        userManager.updateUser(user.getId(),user);
        siteAdminsList = siteAdminsDAO.findAllByAdminId(user.getId());
        assertEquals(3,siteAdminsList.size());
    }

    @Test
    public void testGetPendingActivationList(){
       Site site1 = new Site();
       site1.setName("site1");
       site1.setSiteCode("AMZ");
       site1 = siteDAO.save(site1);
       Site site2 = new Site();
       site2.setName("site2");
       site2 = siteDAO.save(site2);
       for(int i=0;i<10;i++){
           User user = new User();
           if(i%2 == 0 && i<8){//0 2 4 6
             user.setFullName("kalyani");
             user.setStatus(UserStatus.ACTIVATION_PENDING);
             List<String> siteId = new ArrayList<>();
             siteId.add(site1.getId());
             user.setSiteIds(siteId);
             userDAO.save(user);
           }else{// 1 3 5 7 8 9
               user.setFullName("kalyani");
               List<String> siteId = new ArrayList<>();
               user.setStatus(UserStatus.ACTIVATION_PENDING);
               user.setPhoneNumber("7673970975");
               siteId.add(site2.getId());
               user.setSiteIds(siteId);
               userDAO.save(user);
           }
       }
       Page<User> userPage = userManager.getActivationPendingUsers(new JSONObject());
       List<User> users = userPage.getContent();
       assertEquals(10,users.size());
       JSONObject data = new JSONObject();
       data.put("siteId",site1.getId());
       userPage = userManager.getActivationPendingUsers(data);
       users = userPage.getContent();
       assertEquals(4,users.size());
       data = new JSONObject();
       data.put("siteId",site1.getId());
       data.put("phoneNumber","7673970975");
       userPage = userManager.getActivationPendingUsers(data);
       users = userPage.getContent();
       assertEquals(0,users.size());
       data = new JSONObject();
       data.put("name","kalyani");
       data.put("phoneNumber","7673970975");
       userPage = userManager.getActivationPendingUsers(data);
       users = userPage.getContent();
       assertEquals(6,users.size());
    }
    @Test
    public void testDeleteUser() throws ParseException {
        User user = new User("sanju", "sanjuM", 10);
        userDAO.save(user);
        String id = user.getId();
        ShiftTemplate shiftTemplate = new ShiftTemplate();
        shiftTemplate.setName("abc");
        shiftTemplate.setStartTime(new HoursAndMinutes(00, 00));
        shiftTemplate.setEndTime(new HoursAndMinutes(24, 00));
        shiftTemplateDAO.save(shiftTemplate);
        String shiftTemplateId = shiftTemplate.getId();
        Client client = new Client();
        client.setClientName("anthariksh");
        clientDAO.save(client);
        String clientId = client.getId();
        Site site = new Site();
        site.setName("Hyd");
        siteDAO.save(site);
        String siteId = site.getId();
        Calendar calendar = Calendar.getInstance();
        String startDateStr= serviceUtils.formatDate(calendar.getTime());
        calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) + 5);
        String endDateStr= serviceUtils.formatDate(calendar.getTime());
        List<UserShift> userShiftList = userManager.assignShiftToUser(shiftTemplateId, id, siteId, clientId, startDateStr,endDateStr, 10, "");
        userManager.deleteUser(id);
        for(UserShift userShift : userShiftList) {
            Optional<UserShift> shift = userShiftDAO.findById(userShift.getId());
            assertEquals(UserShiftStatus.SHIFT_SUSPENDED.toString(), shift.get().getStatus().toString());
        }

    }
}
