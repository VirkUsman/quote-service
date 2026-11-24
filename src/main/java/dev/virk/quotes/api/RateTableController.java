package dev.virk.quotes.api;

import dev.virk.quotes.api.RateTableDtos.CreateRateTable;
import dev.virk.quotes.api.RateTableDtos.RateTableView;
import dev.virk.quotes.rates.RateTableService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rate-tables")
public class RateTableController {

    private final RateTableService service;
    private final Clock clock;

    public RateTableController(RateTableService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @PostMapping
    ResponseEntity<RateTableView> create(@Valid @RequestBody CreateRateTable body) {
        RateTableView view = RateTableView.of(service.create(body.toNew()));
        return ResponseEntity.created(URI.create("/api/rate-tables/" + view.version())).body(view);
    }

    @GetMapping
    List<RateTableView> list() {
        return service.list().stream().map(RateTableView::of).toList();
    }

    @GetMapping("/active")
    RateTableView active() {
        return RateTableView.of(service.activeAt(clock.instant()));
    }

    @GetMapping("/{version}")
    RateTableView get(@PathVariable int version) {
        return RateTableView.of(service.get(version));
    }
}
