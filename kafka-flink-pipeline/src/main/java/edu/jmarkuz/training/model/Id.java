package edu.jmarkuz.training.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.NoArgsConstructor;

@lombok.Data
@NoArgsConstructor
@JsonSerialize
public class Id {
    String topic;
    int partition;
    int offset;
    long timestamp;
}
