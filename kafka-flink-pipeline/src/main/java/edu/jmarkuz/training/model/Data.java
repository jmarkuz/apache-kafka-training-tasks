package edu.jmarkuz.training.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@lombok.Data
@NoArgsConstructor
@JsonSerialize
public class Data {
    private String schema;
    private Meta meta;
    private String id;
    private String type;
    private String title;
    @JsonProperty("title_url")
    private String titleUrl;
    private String comment;
    @Setter
    @Getter
    private String user;
    private String serverUrl;
    private String serverName;
    private String wiki;
    @JsonProperty("parsedcomment")
    private String parsedComment;
    @Getter
    private boolean bot;
}
