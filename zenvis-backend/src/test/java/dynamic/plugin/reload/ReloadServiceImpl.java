package dynamic.plugin.reload;

import org.springframework.stereotype.Service;

@Service
public class ReloadServiceImpl implements ReloadService {

    @Override
    public String value() {
        return "ok";
    }
}
