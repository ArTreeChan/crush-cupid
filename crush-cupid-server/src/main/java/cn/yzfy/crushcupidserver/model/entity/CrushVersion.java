package cn.yzfy.crushcupidserver.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 暗恋对象版本快照
 *
 * @TableName crush_version
 */
@Data
@TableName("crush_version")
public class CrushVersion {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long crushId;

    private Integer version;

    /** 快照（JSON 文本） */
    private String snapshot;

    private String reason;

    private Date createdAt;
}
