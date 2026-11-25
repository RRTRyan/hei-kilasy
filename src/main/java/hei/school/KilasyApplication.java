package hei.school;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class KilasyApplication {

    public static void main(String[] args) {
        SpringApplication.run(KilasyApplication.class, args);
    }
}

/**
 * Outbound SMS gateway. Replace the console implementation with a real provider later.
 */
interface SmsSender {

    void send(String phoneNumber, String message);
}

/**
 * Simulated SMS provider: writes messages to the console.
 */
@Component
class ConsoleSmsSender implements SmsSender {

    @Override
    public void send(String phoneNumber, String message) {
        System.out.println(message);
    }
}
