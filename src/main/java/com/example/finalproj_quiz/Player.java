package com.example.finalproj_quiz;

public class Player {
    private Long id;
    private String name;
    private String role;

    public Player(String name){
        this.name = name;
    }

    public Player(){}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }



    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}

