package org.radoslawzerek.cafemanagementsystem.serviceimpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.radoslawzerek.cafemanagementsystem.JWT.CustomerUsersDetailsService;
import org.radoslawzerek.cafemanagementsystem.JWT.JwtFilter;
import org.radoslawzerek.cafemanagementsystem.JWT.JwtUtil;
import org.radoslawzerek.cafemanagementsystem.constants.CafeConstants;
import org.radoslawzerek.cafemanagementsystem.dao.UserDao;
import org.radoslawzerek.cafemanagementsystem.entity.User;
import org.radoslawzerek.cafemanagementsystem.service.UserService;
import org.radoslawzerek.cafemanagementsystem.utils.CafeUtils;
import org.radoslawzerek.cafemanagementsystem.utils.EmailUtils;
import org.radoslawzerek.cafemanagementsystem.wrapper.UserWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserDao userDao;

    private final AuthenticationManager authenticationManager;

    private final CustomerUsersDetailsService customerUsersDetailsService;

    private final JwtUtil jwtUtil;

    private final JwtFilter jwtFilter;

    private final EmailUtils emailUtils;

    @Override
    public ResponseEntity<String> signup(Map<String, String> requestMap) {
        log.info("Inside signup {}", requestMap);
        try {
            if (this.validateSignUpMap(requestMap)) {
                User user = userDao.findByEmailId(requestMap.get("email"));
                if (Objects.isNull(user)) {
                    userDao.save(this.getUserFromMap(requestMap));
                    return CafeUtils.getResponseEntity("Successfully Registered", HttpStatus.OK);
                } else {
                    return CafeUtils.getResponseEntity(CafeConstants.EMAIL_EXISTS, HttpStatus.BAD_REQUEST);
                }

            } else {
                return CafeUtils.getResponseEntity(CafeConstants.INVALID_DATA, HttpStatus.BAD_REQUEST);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return CafeUtils.getResponseEntity(CafeConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<String> login(Map<String, String> requestMap) {
        log.info("Inside login");
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    requestMap.get("email"), requestMap.get("password")));
            if (authentication.isAuthenticated()) {
                if (customerUsersDetailsService.getUserDetail().getStatus().equalsIgnoreCase("true")) {
                    return new ResponseEntity<String>("{\"token\":\"" +
                            jwtUtil.generateToken(customerUsersDetailsService.getUserDetail().getEmail(),
                                    customerUsersDetailsService.getUserDetail().getRole()) + "\"}", HttpStatus.OK);
                } else {
                    return new ResponseEntity<String>("{\"message\":\"" + "Wait for admin approval." + "\"}", HttpStatus.BAD_REQUEST);
                }
            }

        } catch (Exception exception) {
            log.error("{}", exception);
        }
        return new ResponseEntity<String>("{\"message\":\"" + "Bad credentials." + "\"}", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ResponseEntity<List<UserWrapper>> getAllUsers() {
        try {
            if (jwtFilter.isAdmin()) return new ResponseEntity<>(userDao.getAllUser(), HttpStatus.OK);
            else return new ResponseEntity<>(new ArrayList<>(), HttpStatus.UNAUTHORIZED);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return new ResponseEntity<>(new ArrayList<>(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<String> update(Map<String, String> requestMap) {
        try {
            if (jwtFilter.isAdmin()) {
                Optional<User> user = userDao.findById(Long.parseLong(requestMap.get("id")));
                if (user.isPresent()) {
                    userDao.updateStatus(requestMap.get("status"), Long.parseLong(requestMap.get("id")));
                    this.sendMailToAllAdmin(requestMap.get("status"), user.get().getEmail(), userDao.getAllAdmin());
                    return CafeUtils.getResponseEntity("User status updated successfully", HttpStatus.OK);
                } else {
                    return CafeUtils.getResponseEntity("User id does not exist", HttpStatus.NO_CONTENT);
                }
            } else return CafeUtils.getResponseEntity(CafeConstants.UNAUTHORIZED_ACCESS, HttpStatus.UNAUTHORIZED);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return CafeUtils.getResponseEntity(CafeConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void sendMailToAllAdmin(String status, String user, List<String> allAdmin) {
        allAdmin.remove(jwtFilter.getCurrentUser());
        if (status != null && status.equalsIgnoreCase("true"))
            emailUtils.sendSimpleMessage(jwtFilter.getCurrentUser(), "Account Approved", "User:- " + user + "\n is approved by \nADMIN:- " + jwtFilter.getCurrentUser(), allAdmin);
        else
            emailUtils.sendSimpleMessage(jwtFilter.getCurrentUser(), "Account Disabled", "User:- " + user + "\n is disabled by \nADMIN:- " + jwtFilter.getCurrentUser(), allAdmin);
    }

    private boolean validateSignUpMap(Map<String, String> requestMap) {
        return requestMap.containsKey("name")
                && requestMap.containsKey("phone")
                && requestMap.containsKey("email")
                && requestMap.containsKey("password");
    }

    private User getUserFromMap(Map<String, String> requestMap) {
        User user = new User();
        user.setName(requestMap.get("name"));
        user.setPhone(requestMap.get("phone"));
        user.setEmail(requestMap.get("email"));
        user.setPassword(requestMap.get("password"));
        user.setStatus("false");
        user.setRole("user");
        return user;
    }
}
