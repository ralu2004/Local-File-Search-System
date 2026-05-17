package app.model;

import app.search.widget.Widget;

import java.util.List;

public record SearchResponse(
        List<RankedSearchResult> results,
        List<Widget> widgets
) {}
