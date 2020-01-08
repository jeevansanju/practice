package com.wizzard.model;

public enum UserShiftStatus {

    INIT("INIT"),
    ATTENDANCE_MARKED("ATTENDANCE_MARKED"),
    SHIFT_IN_PROGRESS("SHIFT_IN_PROGRESS"),
    SHIFT_ENDED("SHIFT_ENDED"),
    SHIFT_SUSPENDED("SHIFT_SUSPENDED"),
    SHIFT_CLOSED_BY_SUPERVISOR("SHIFT_CLOSED_BY_SUPERVISOR"),
    SHIFT_CANCELLED_BY_SUPERVISOR("SHIFT_CANCELLED_BY_SUPERVISOR");

    private final String key;

    UserShiftStatus(final String key) {
        this.key = key;
    }

    public static UserShiftStatus findByValue(String abbr){
        for(UserShiftStatus v : values()){
            if( v.toString().equals(abbr)){
                return v;
            }
        }
        return null;
    }
    public String getKey() {
        return this.key;
    }
    @Override
    public String toString() {
        return this.key;
    }
}
