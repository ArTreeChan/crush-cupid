package cn.yzfy.crushcupidserver;

import cn.yzfy.crushcupidserver.agent.proactive.ProactiveProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("cn.yzfy.crushcupidserver.model.mapper")
@EnableScheduling
@EnableConfigurationProperties(ProactiveProperties.class)
public class CrushCupidServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrushCupidServerApplication.class, args);
    }

}
