package ds.namingnote;

import ds.namingnote.Controller.NodeController;
import ds.namingnote.Multicast.MulticastListener;
import ds.namingnote.Service.NodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@SpringBootApplication
@EnableScheduling
public class NamingNoteApplication {

    public static void main(String[] args) {
        SpringApplication.run(NamingNoteApplication.class, args);
    }

    @Component
    public static class StartupName implements CommandLineRunner {

        @Value("${name}")
        private String serviceName;

        @Autowired
        private NodeService nodeService;
        @Override
        public void run(String... args) throws Exception {
            if (serviceName != null) {
                System.out.println("Service name: " + serviceName);
                nodeService.setNameBegin(serviceName);
            } else {
                System.out.println("No service name provided. Use --name=YourName");
            }
        }


    }



}
