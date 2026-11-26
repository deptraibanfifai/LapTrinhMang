// File: src/com/mail/model/User.java
package com.mail.model;

import java.sql.Timestamp;

public class User {
    private int id;
    private String username;
    private String password;
    private String email;
    private String fullName;
    private Timestamp createdAt;
    private Timestamp lastLogin;

    // Constructors
    public User() {}

    public User(String username, String password, String email, String fullName) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.fullName = fullName;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getLastLogin() { return lastLogin; }
    public void setLastLogin(Timestamp lastLogin) { this.lastLogin = lastLogin; }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', email='" + email + "'}";
    }
}