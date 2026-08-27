package com.coolxer.service.system;

import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.vo.PushTaskVo;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface PushTaskService {

    Object proxy(HttpServletRequest request);

    boolean createAndStart(PushTaskDto pushTaskDto);

    boolean updateAndStart(Integer id, PushTaskDto pushTaskDto);

    default boolean update(Integer id, PushTaskDto pushTaskDto) {
        return false;
    }

    default boolean toggle(Integer id) {
        return false;
    }

    default boolean delete(Integer id) {
        return false;
    }

    List<PushTaskVo> findAll();

    PushTaskVo findById(Integer id);

    List<PushTaskVo> findBySourceMark(String sourceMark);

    boolean deleteBySourceMark(String sourceMark);

    String getLog(Integer id, String logType);

    String detectFormat(String content);
}
