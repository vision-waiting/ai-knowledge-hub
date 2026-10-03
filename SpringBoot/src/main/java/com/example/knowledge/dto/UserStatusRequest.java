package com.example.knowledge.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
public record UserStatusRequest(@jakarta.validation.constraints.NotNull @Min(value = 0, message = "状态只能是0或1") @Max(value = 1, message = "状态只能是0或1") Integer status) {}
