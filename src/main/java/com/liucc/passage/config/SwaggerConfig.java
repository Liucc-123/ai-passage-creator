package com.liucc.passage.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 配置类
 */
@Configuration
public class SwaggerConfig {

    /**
     * 定义 OpenAPI 规范
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Passage Creator API")
                        .description("AI 爆款文章创作器 - 接口文档")
                        .version("1.0.0"));
    }
}
