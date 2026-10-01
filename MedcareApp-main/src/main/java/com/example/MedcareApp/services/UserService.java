package com.example.MedcareApp.services;

import com.example.MedcareApp.Entity.user;
import com.example.MedcareApp.Interafce.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public user createUser(user newUser) {
        if (!userRepository.findAllByEmailIdIgnoreCase(newUser.getEmailId()).isEmpty()) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));
        return userRepository.save(newUser);
    }

    public user authenticate(String emailId, String rawPassword) {
        List<user> matchingUsers = userRepository.findAllByEmailIdIgnoreCase(emailId);
        if (matchingUsers.size() != 1 || rawPassword == null) {
            return null;
        }
        user existingUser = matchingUsers.get(0);
        if (existingUser.getPassword() == null) return null;

        String storedPassword = existingUser.getPassword();
        if (passwordEncoder.matches(rawPassword, storedPassword)) {
            return existingUser;
        }

        if (!storedPassword.startsWith("$2") && storedPassword.equals(rawPassword)) {
            existingUser.setPassword(passwordEncoder.encode(rawPassword));
            return userRepository.save(existingUser);
        }

        return null;
    }

    public List<user> getAllUsers() {
        return userRepository.findAll();
    }

    public List<user> findByUserId(String userId) {
        return userRepository.findByUserId(userId);
    }

    public user updateUser(String userId, user updatedUser) {
        List<user> existingUsers = userRepository.findByUserId(userId);

        if (!existingUsers.isEmpty()) {
            user existingUser = existingUsers.get(0);  // Use the first match
            existingUser.setPassword(updatedUser.getPassword());
            existingUser.setEmailId(updatedUser.getEmailId());
            existingUser.setMobileNo(updatedUser.getMobileNo());
            return userRepository.save(existingUser);
        } else {
            // Create new user if none found
            updatedUser.setUserId(userId);
            return userRepository.save(updatedUser);
        }
    }

    public user getUserByEmail(String emailId) {
        List<user> matchingUsers = userRepository.findAllByEmailIdIgnoreCase(emailId);
        return matchingUsers.size() == 1 ? matchingUsers.get(0) : null;
    }
    public void deleteUser(String userId) {
        userRepository.deleteByUserid(userId);



    }
}

