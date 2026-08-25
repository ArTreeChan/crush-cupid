package cn.yzfy.crushcupidserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cn.yzfy.crushcupidserver.model.mapper")
public class CrushCupidServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrushCupidServerApplication.class, args);
    }

}
