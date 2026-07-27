package demo.service;
import java.util.List;
import java.util.stream.Collectors;
public class StreamProcessService {
    public List<String> processDevelopers(List<Developer> developers) {
        return developers.stream()
            .filter(dev -> dev.getDepartment().equals("개발팀"))
            .filter(dev -> dev.getSalary() >= 5000)
            .map(Developer::getName)
            .collect(Collectors.toList());
    }
}
class Developer {
    private String name;
    private String department;
    private double salary;
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public double getSalary() { return salary; }
}
