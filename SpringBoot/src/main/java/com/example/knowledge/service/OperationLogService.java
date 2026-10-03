package com.example.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.knowledge.entity.OperationLog;
import com.example.knowledge.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OperationLogService {
    private final OperationLogMapper mapper;
    public OperationLogService(OperationLogMapper mapper) { this.mapper = mapper; }
    public List<OperationLog> recent() { return mapper.selectList(new QueryWrapper<OperationLog>().orderByDesc("id").last("limit 200")); }
}
