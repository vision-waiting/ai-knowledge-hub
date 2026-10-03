# Spring Boot 开发指南

## 自动配置

Spring Boot 自动配置根据类路径中的依赖、配置属性和已经声明的 Bean 决定是否启用某项配置。
@SpringBootApplication 组合了 @SpringBootConfiguration、@EnableAutoConfiguration 和 @ComponentScan。
@EnableAutoConfiguration 导入候选自动配置类，并通过 @ConditionalOnClass、@ConditionalOnMissingBean 等条件注解判断是否生效。
开发者声明自己的 Bean 后，相关的默认配置通常会退让。可以使用 exclude 排除特定自动配置。

## Starter

Starter 将某类应用所需的依赖集中声明，降低手动选择依赖版本的成本。例如 spring-boot-starter-web 包含构建 Servlet Web 应用的常用依赖。
Starter 与自动配置配合使用，但 Starter 本身不是自动配置执行器。

## Spring 与 Spring Boot

Spring Framework 提供依赖注入、事务管理和 Web MVC 等基础能力。Spring Boot 基于 Spring Framework，通过自动配置、Starter 和嵌入式服务器简化应用启动和部署。
Spring Boot 没有替代 Spring Framework；它使 Spring 应用的配置和运维更加便捷。

## 配置优先级

应用可以使用 application.yml、环境变量和命令行参数配置。命令行参数通常覆盖配置文件中的同名属性。
数据库密码和模型密钥应由环境变量或受保护的配置注入，不应写入源代码。
