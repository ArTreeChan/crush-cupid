package cn.yzfy.crushcupidserver.model.service.impl;

import cn.yzfy.crushcupidserver.model.entity.CrushReport;
import cn.yzfy.crushcupidserver.model.mapper.CrushReportMapper;
import cn.yzfy.crushcupidserver.model.service.CrushReportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 针对表【crush_report】的数据库操作 Service 实现
 */
@Service
public class CrushReportServiceImpl extends ServiceImpl<CrushReportMapper, CrushReport>
        implements CrushReportService {

    @Override
    public List<CrushReport> listByCrushId(Long crushId) {
        return list(new LambdaQueryWrapper<CrushReport>()
                .eq(CrushReport::getCrushId, crushId)
                .orderByDesc(CrushReport::getId));
    }

    @Override
    public boolean existsOnDate(Long crushId, LocalDate date) {
        return list(new LambdaQueryWrapper<CrushReport>()
                .eq(CrushReport::getCrushId, crushId)
                .eq(CrushReport::getReportDate, java.sql.Date.valueOf(date))
                .last("limit 1")).size() > 0;
    }

    @Override
    public List<CrushReport> listByCrushOrderByDate(Long crushId) {
        return list(new LambdaQueryWrapper<CrushReport>()
                .eq(CrushReport::getCrushId, crushId)
                .orderByDesc(CrushReport::getReportDate));
    }
}
