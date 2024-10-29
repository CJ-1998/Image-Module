package image.module.resize;

import image.module.resize.dto.ResizeRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "data")
public interface DataClient {

  @PostMapping("/image/create/resizeImage")
  void createResizeImage(@RequestBody ResizeRequestDto resizeRequestDto);
}
