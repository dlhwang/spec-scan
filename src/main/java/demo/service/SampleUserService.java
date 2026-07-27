package demo.service;

public class SampleUserService {
    public boolean isUserActive(Long userId) {
        return userId != null && userId > 0;
    }
}
