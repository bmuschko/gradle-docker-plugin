package com.bmuschko.gradle.docker.fixtures

final class DockerSpringBootApplicationPluginFixture {

    private DockerSpringBootApplicationPluginFixture() {}

    static void writeSpringBootApplicationClasses(File projectDir) {
        File packageDir = new File(projectDir, 'src/main/java/com/bmuschko/gradle/docker/springboot')

        if (!packageDir.mkdirs()) {
            throw new IOException("Unable to create package directory $packageDir")
        }

        new File(packageDir, 'HelloController.java').text = helloWorldControllerClass()
        new File(packageDir, 'Application.java').text = applicationClass()
    }

    private static String helloWorldControllerClass() {
        """
            package com.bmuschko.gradle.docker.springboot;
            
            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.web.bind.annotation.RequestMapping;
            
            @RestController
            public class HelloController {
            
                @RequestMapping("/")
                public String index() {
                    return "Greetings from Spring Boot!";
                }
            
            }
        """
    }

    private static String applicationClass() {
        """
            package com.bmuschko.gradle.docker.springboot;
            
            import java.util.Arrays;

            import org.springframework.boot.CommandLineRunner;
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.context.ApplicationContext;
            import org.springframework.context.annotation.Bean;
            
            @SpringBootApplication
            public class Application {
                public static void main(String[] args) {
                    SpringApplication.run(Application.class, args);
                }
            
                @Bean
                public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
                    return args -> {
                        System.out.println("Let's inspect the beans provided by Spring Boot:");
                        String[] beanNames = ctx.getBeanDefinitionNames();
                        Arrays.sort(beanNames);
                        for (String beanName : beanNames) {
                            System.out.println(beanName);
                        }
                    };
                }
            }
        """
    }
}
