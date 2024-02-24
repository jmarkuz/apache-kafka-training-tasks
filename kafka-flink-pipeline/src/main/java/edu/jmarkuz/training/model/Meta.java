package edu.jmarkuz.training.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.NoArgsConstructor;

@lombok.Data
@NoArgsConstructor
@JsonSerialize
public class Meta {
    private String uri;
    @JsonProperty("request_id")
    private String request_id;
    private String id;
    private String dt;
    private String domain;
    private String stream;
    private String topic;
    private int partition;
    private long offset;
}
