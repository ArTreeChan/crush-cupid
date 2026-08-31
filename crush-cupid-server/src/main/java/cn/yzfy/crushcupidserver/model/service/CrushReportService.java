package cn.yzfy.crushcupidserver.model.service;

import cn.yzfy.crushcupidserver.model.entity.CrushReport;
import com.baomidou.mybatisplus.spring.service.IService;

import java.time.LocalDate;
import java.util.List;

/**
 * 针对表【crush_report】的数据库操作 Service
 */
public interface CrushReportService extends IService<CrushReport> {

    /** 查询某暗恋对象的关系报告历史（新→旧） */
    List<CrushReport> listByCrushId(Long crushId);

    /** 判断某 crush 某日是否已生成过报告（用于定时任务每日去重） */
    boolean existsOnDate(Long crushId, LocalDate date);

    /** 某 crush 的全部报告历史（用于定时任务的轻量判断） */
    List<CrushReport> listByCrushOrderByDate(Long crushId);
}
