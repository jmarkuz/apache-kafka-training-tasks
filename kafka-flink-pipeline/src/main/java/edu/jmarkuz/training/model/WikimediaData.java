package edu.jmarkuz.training.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.NoArgsConstructor;

@lombok.Data
@NoArgsConstructor
@JsonSerialize
public class WikimediaData {
    Id id;
    @Getter
    @JsonProperty("$schema")
    Data data;
}
