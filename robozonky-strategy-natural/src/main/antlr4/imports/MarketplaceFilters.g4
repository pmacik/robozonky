grammar MarketplaceFilters;

import CommonFilters, Tokens;

@header {
    import java.util.Collection;
    import java.util.ArrayList;
    import java.util.Map;
    import java.util.HashMap;
    import com.github.robozonky.strategy.natural.*;
    import com.github.robozonky.strategy.natural.conditions.*;
}

marketplaceFilterExpression returns [Collection<MarketplaceFilter> primary, Collection<MarketplaceFilter> secondary]
    @init {
        $primary = new ArrayList<>();
        $secondary = new ArrayList<>();
    } :
    (
        (j=jointMarketplaceFilter {
            $primary.add($j.result);
            $secondary.add($j.result);
        }) | (p=primaryMarketplaceFilter {
            $primary.add($p.result);
        }) | (s=secondaryMarketplaceFilter {
            $secondary.add($s.result);
        })
    )*
;

sellFilterExpression returns [Collection<MarketplaceFilter> result]:
    { $result = new ArrayList<>(0); }
    (
        (j=sellMarketplaceFilter { $result.add($j.result); })
    )*
;

jointMarketplaceFilter returns [MarketplaceFilter result]:
    { $result = new MarketplaceFilter(); }
    'Ignorovat vše, kde: ' r=jointMarketplaceFilterConditions { $result.when($r.result); }
    ('(Ale ne když: ' s=jointMarketplaceFilterConditions { $result.butNotWhen($s.result); } ')')?
;

primaryMarketplaceFilter returns [MarketplaceFilter result]:
    { $result = new MarketplaceFilter(); }
    'Ignorovat úvěr, kde: ' r=primaryMarketplaceFilterConditions { $result.when($r.result); }
    ('(Ale ne když: ' s=primaryMarketplaceFilterConditions { $result.butNotWhen($s.result); } ')')?
;

secondaryMarketplaceFilter returns [MarketplaceFilter result]:
    { $result = new MarketplaceFilter(); }
    'Ignorovat participaci, kde: ' r=secondaryMarketplaceFilterConditions { $result.when($r.result); }
    ('(Ale ne když: ' s=secondaryMarketplaceFilterConditions { $result.butNotWhen($s.result); } ')')?
;

sellMarketplaceFilter returns [MarketplaceFilter result]:
    { $result = new MarketplaceFilter(); }
    'Prodat participaci, kde: ' r=secondaryMarketplaceFilterConditions { $result.when($r.result); }
    ('(Ale ne když: ' s=secondaryMarketplaceFilterConditions { $result.butNotWhen($s.result); } ')')?
;

jointMarketplaceFilterConditions returns [Collection<MarketplaceFilterCondition> result]:
    { Collection<MarketplaceFilterCondition> result = new LinkedHashSet<>(); }
    (c1=jointMarketplaceFilterCondition { result.add($c1.result); } '; ')*
    c2=jointMarketplaceFilterCondition { result.add($c2.result); } DOT
    { $result = result; }
;

primaryMarketplaceFilterConditions returns [Collection<MarketplaceFilterCondition> result]:
    { Collection<MarketplaceFilterCondition> result = new LinkedHashSet<>(); }
    (c1=primaryMarketplaceFilterCondition { result.add($c1.result); } '; ')*
    c2=primaryMarketplaceFilterCondition { result.add($c2.result); } DOT
    { $result = result; }
;

secondaryMarketplaceFilterConditions returns [Collection<MarketplaceFilterCondition> result]:
    { Collection<MarketplaceFilterCondition> result = new LinkedHashSet<>(); }
    (c1=secondaryMarketplaceFilterCondition { result.add($c1.result); } '; ')*
    c2=secondaryMarketplaceFilterCondition { result.add($c2.result); } DOT
    { $result = result; }
;

jointMarketplaceFilterCondition returns [MarketplaceFilterCondition result]:
    c1=regionCondition { $result = $c1.result; }
    | c2=ratingCondition { $result = $c2.result; }
    | c3=incomeCondition { $result = $c3.result; }
    | c4=purposeCondition { $result = $c4.result; }
    | c5=storyCondition { $result = $c5.result; }
    | c6=termCondition { $result = $c6.result; }
    | c8=interestCondition { $result = $c8.result; }
;

primaryMarketplaceFilterCondition returns [MarketplaceFilterCondition result]:
    c1=regionCondition { $result = $c1.result; }
    | c2=ratingCondition { $result = $c2.result; }
    | c3=incomeCondition { $result = $c3.result; }
    | c4=purposeCondition { $result = $c4.result; }
    | c5=storyCondition { $result = $c5.result; }
    | c6=termCondition { $result = $c6.result; }
    | c7=amountCondition { $result = $c7.result; }
    | c8=interestCondition { $result = $c8.result; }
;

secondaryMarketplaceFilterCondition returns [MarketplaceFilterCondition result]:
    c1=regionCondition { $result = $c1.result; }
    | c2=ratingCondition { $result = $c2.result; }
    | c3=incomeCondition { $result = $c3.result; }
    | c4=purposeCondition { $result = $c4.result; }
    | c5=storyCondition { $result = $c5.result; }
    | c6=termCondition { $result = $c6.result; }
    | c8=interestCondition { $result = $c8.result; }
    | c9=relativeTermCondition { $result = $c9.result; }
    | c10=elapsedTermCondition { $result = $c10.result; }
    | c11=elapsedRelativeTermCondition { $result = $c11.result; }
;
