package com.automations;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestWeb {
    private WebDriver driver;
    private WebDriverWait wait;
    private final String linkTest = "https://acp-test.mk.com.vn:1982/#/login";
    private final String userName = "testuatmk@mailinator.com";
    private final String password = "TestMK@1234";
    private final String accountName = "TESTUAT";


    @BeforeClass
    public void setUp() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        driver.get(linkTest);
    }
    @AfterClass
    public void teardown() {
        driver.quit();
    }

    @Test(priority = 0)
    public void testLogin() {

        // Điền thông tin đăng nhập
        WebElement usernameField = driver.findElement(By.xpath("(//input[1])[1]"));
        WebElement passwordField = driver.findElement(By.xpath("(//input[1])[2]"));

        usernameField.sendKeys(userName);
        passwordField.sendKeys(password);
        passwordField.submit();

        // Chờ trang nhập OTP xuất hiện
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("otpInput")));

        // Mở tab mới để truy cập Mailinator
        String mainTab = driver.getWindowHandle();
        ((JavascriptExecutor) driver).executeScript("window.open('https://www.mailinator.com/', '_blank');");

        // Chuyển sang tab Mailinator
        for (String tab : driver.getWindowHandles()) {
            if (!tab.equals(mainTab)) {
                driver.switchTo().window(tab);
                break;
            }
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Nhập email vào ô tìm kiếm
        WebElement inbox = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("search")));
        inbox.sendKeys("testuatmk");
        WebElement buttonGo = driver.findElement(By.xpath("(//button[1])[1]"));
        buttonGo.click();

        // Chờ email OTP xuất hiện
        WebElement email = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("(//tr[1])[2]")));
        scrollToElement(email);
        email.click();

        // Lấy mã OTP từ email
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Kiểm tra danh sách iframe
        List<WebElement> iframes = driver.findElements(By.tagName("iframe"));

        // Cố gắng lấy nội dung email
        String fullContent = "";
        for (WebElement iframe : iframes) {
            driver.switchTo().frame(iframe);
            fullContent = driver.findElement(By.tagName("body")).getText();
            if (!fullContent.trim().isEmpty()) {
                break; // Dừng lại nếu lấy được nội dung
            }
            driver.switchTo().defaultContent();
        }

        // Nếu không tìm thấy nội dung, thử lấy bằng JavaScript
        if (fullContent.trim().isEmpty()) {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            fullContent = (String) js.executeScript("return document.body.innerText;");
            System.out.println("Try JS");
        }

        // Chụp ảnh màn hình nếu không có nội dung email
        if (fullContent.trim().isEmpty()) {
            takeScreenshot("error_no_email.png");
            throw new RuntimeException("Không tìm thấy nội dung email! Đã chụp ảnh màn hình.");
        }

        // Tìm OTP trong nội dung email bằng Regex
        String otpCode = "";
        try {
            Pattern pattern = Pattern.compile("\\b\\d{6}\\b"); // Mã OTP có 6 chữ số
            Matcher matcher = pattern.matcher(fullContent);
            if (matcher.find()) {
                otpCode = matcher.group(0);
            } else {
                takeScreenshot("error_no_otp.png");
                throw new RuntimeException("Không tìm thấy OTP trong email! Đã chụp ảnh màn hình.");
            }
        } catch (Exception e) {
            takeScreenshot("error_regex.png");
            throw new RuntimeException("Lỗi khi tìm OTP: " + e.getMessage());
        }

        // Quay lại tab chính
        driver.switchTo().window(mainTab);

        List<WebElement> otpInputs = driver.findElements(By.className("inputChild"));

        // Kiểm tra đủ số ô nhập OTP
        if (otpInputs.size() == otpCode.length()) {
            for (int i = 0; i < otpCode.length(); i++) {
                otpInputs.get(i).sendKeys(String.valueOf(otpCode.charAt(i)));
            }
        } else {
            throw new RuntimeException("Số lượng ô nhập OTP không khớp với mã OTP");
        }

        WebElement buttonConfirm = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//button[text()='Xác nhận']")));
        buttonConfirm.click();

        System.out.println("Login success with OTP: " + otpCode);

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    @Test(priority = 1)
    public void verifyLogin(){
        //Click menu báo cáo
        clickElementByXpath("//span[text() = \"Báo cáo\"]");
        clickElementByXpath("//a[text() = \"Báo cáo log hệ thống\"]");

        //So sánh tài khoản đăng nhập với log đăng nhập của hệ thống
        try {
            WebElement loginLog = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("(//tr[@class = \"ant-table-row ant-table-row-level-0\"][1])/td[2]")));
            Assert.assertEquals(loginLog.getText(),accountName,"Sai tài khoản");
        }catch (Exception e){
            System.out.println("Không tìm thấy element LoginLog");
        }
    }

    private void takeScreenshot(String fileName) {
        try {
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(screenshot, new File(fileName));
            System.out.println("Đã lưu ảnh chụp màn hình: " + fileName);
        } catch (IOException e) {
            System.err.println("Lỗi khi chụp ảnh màn hình: " + e.getMessage());
        }
    }


    private void clickElementByXpath( String xpath){
        try {
            WebElement elementXpath = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
            elementXpath.click();
        }catch (Exception e){
            System.out.println("Không tìm thấy phần tử " + xpath);
        }
    }


    public void scrollToElement(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
    }
}
