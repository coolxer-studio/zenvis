package dynamic.plugin.reload;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReloadController {

    @Autowired
    private ReloadService reloadService;

    @GetMapping("/value")
    public String value() {
        return reloadService.value();
    }
}
