package com.bmuschko.gradle.docker.fixtures

final class DockerJavaApplicationPluginFixture {

    private DockerJavaApplicationPluginFixture() {}

    static void writeJettyMainClass(File projectDir) {
        File packageDir = new File(projectDir, 'src/main/java/com/bmuschko/gradle/docker/application')
        createDirectory(packageDir)
        File jettyMainClassFile = new File(packageDir, 'JettyMain.java')
        jettyMainClassFile.text = jettyMainClass()
    }

    private static String jettyMainClass() {
        """
            package com.bmuschko.gradle.docker.application;
            
            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            import javax.servlet.ServletException;
            
            import java.io.IOException;
            
            import org.eclipse.jetty.server.Server;
            import org.eclipse.jetty.server.Request;
            import org.eclipse.jetty.server.handler.AbstractHandler;
            
            public class JettyMain extends AbstractHandler {
                public void handle(String target,
                                   Request baseRequest,
                                   HttpServletRequest request,
                                   HttpServletResponse response)
                        throws IOException, ServletException {
                    response.setContentType("text/html;charset=utf-8");
                    response.setStatus(HttpServletResponse.SC_OK);
                    baseRequest.setHandled(true);
                    response.getWriter().println("Hello, Docker!");
                }
            
                public static void main(String[] args) throws Exception {
                    Server server = new Server(8080);
                    server.setHandler(new JettyMain());
                    server.start();
                    server.join();
                }
            }
        """
    }

    static void writePropertiesFile(File projectDir) {
        File resourcesDir = new File(projectDir, 'src/main/resources')
        createDirectory(resourcesDir)
        new File(resourcesDir, 'my.properties').text = 'hello=world'
    }

    private static void createDirectory(File dir) {
        if (!dir.mkdirs()) {
            throw new IOException("Unable to create directory $dir")
        }
    }
}
