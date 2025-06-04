package com.example.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimecontentsDto {
    private LocalTime startTime;
    private LocalTime endTime;
    private int classes;
    private String type;//현재 "CLASS" 고정

}
