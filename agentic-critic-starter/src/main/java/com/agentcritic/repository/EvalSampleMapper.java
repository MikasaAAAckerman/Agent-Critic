package com.agentcritic.repository;

import com.agentcritic.repository.entity.EvalSampleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * eval_sample 表 Mapper —— BaseMapper 提供通用 CRUD（参数化，无字符串拼接）。
 */
@Mapper
public interface EvalSampleMapper extends BaseMapper<EvalSampleEntity> {

    /**
     * 按 source 分组统计。
     * 纯静态 SQL（无任何用户输入拼接），用于页面分组列表。
     */
    @Select("SELECT source, COUNT(*) AS cnt FROM eval_sample GROUP BY source ORDER BY cnt DESC")
    List<Map<String, Object>> countBySource();
}
