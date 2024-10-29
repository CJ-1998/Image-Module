package image.module.convert;

import image.module.convert.dto.OriginalFileInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "data")
public interface DataClient {

  @PostMapping("/image/create/cdnUrl")
  void createCdnUrl(@RequestBody OriginalFileInfo originalFileInfo);
}
