package com.example.recordingsdk30;

public class SampleData {
    private String record_name;
    private String spam_prob;

    public SampleData(String record_name, String spam_prob){
        this.record_name = record_name;
        this.spam_prob = spam_prob;
    }

    public String getRecord_name(){
        return this.record_name;
    }

    public String getSpam_prob(){
        return this.spam_prob;
    }
}
