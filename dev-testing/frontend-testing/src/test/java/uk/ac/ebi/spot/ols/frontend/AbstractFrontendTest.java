package uk.ac.ebi.spot.ols.frontend;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

public class AbstractFrontendTest {
    static WebDriver driver;

    static void setup(){
        driver = new ChromeDriver();


    }
}
