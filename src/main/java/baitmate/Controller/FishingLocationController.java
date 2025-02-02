package baitmate.Controller;

import baitmate.Service.FishingLocationService;
import baitmate.model.FishingLocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FishingLocationController {
    @Autowired
    FishingLocationService fishingLocationInt;

    @GetMapping("/locations")
    public List<FishingLocation> getLocations() {
        return fishingLocationInt.findAllLocations();
    }
}
