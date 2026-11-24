package dev.virk.quotes.api;

import dev.virk.quotes.api.QuoteDtos.CreateQuote;
import dev.virk.quotes.api.QuoteDtos.QuoteView;
import dev.virk.quotes.api.QuoteDtos.SweepResult;
import dev.virk.quotes.quote.QuoteService;
import dev.virk.quotes.quote.QuoteStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService service;

    public QuoteController(QuoteService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<QuoteView> create(@Valid @RequestBody CreateQuote body) {
        QuoteView view = QuoteView.of(service.create(new QuoteService.NewQuote(
                body.customerEmail(), body.destinationZone(), body.weightKg(), body.serviceLevel())));
        return ResponseEntity.created(URI.create("/api/quotes/" + view.id())).body(view);
    }

    @GetMapping
    List<QuoteView> list(@RequestParam(required = false) QuoteStatus status) {
        return service.list(status).stream().map(QuoteView::of).toList();
    }

    @GetMapping("/{id}")
    QuoteView get(@PathVariable long id) {
        return QuoteView.of(service.get(id));
    }

    @PostMapping("/{id}/accept")
    QuoteView accept(@PathVariable long id) {
        return QuoteView.of(service.accept(id));
    }

    /** What the scheduled sweep does, callable on demand. */
    @PostMapping("/expire")
    SweepResult expire() {
        return new SweepResult(service.expireDue());
    }
}
