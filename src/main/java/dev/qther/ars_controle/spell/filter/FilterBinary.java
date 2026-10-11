package dev.qther.ars_controle.spell.filter;

import com.hollingsworth.arsnouveau.api.spell.*;
import com.hollingsworth.arsnouveau.common.util.PortUtil;
import dev.qther.ars_controle.ArsControle;
import dev.qther.ars_controle.registry.ACNames;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public class FilterBinary extends AbstractFilter implements IAdaptiveFilter {
    public static final FilterBinary OR = new FilterBinary(ACNames.GLYPH_FILTER_OR, "Filter: OR", "Only resolves the spell if any of the next 2 Filters are true.", (a, b) -> a.get() || b.get());
    public static final FilterBinary XOR = new FilterBinary(ACNames.GLYPH_FILTER_XOR, "Filter: XOR", "Only resolves the spell if only one of the next 2 Filters are true.", (a, b) -> a.get() != b.get());
    public static final FilterBinary XNOR = new FilterBinary(ACNames.GLYPH_FILTER_XNOR, "Filter: XNOR", "Only resolves the spell if the result of both the next 2 Filters are equal.", (a, b) -> a.get() == b.get());

    private final BiFunction<Supplier<Boolean>, Supplier<Boolean>, Boolean> op;
    public SpellResolver res;
    private final String bookDescription;

    private FilterBinary(String id, String name, String bookDescription, BiFunction<Supplier<Boolean>, Supplier<Boolean>, Boolean> op) {
        super(ArsControle.prefix(id), name);
        this.op = op;
        this.bookDescription = bookDescription;
    }

    @Override
    public String getBookDescription() {
        return this.bookDescription;
    }

    @Override
    public Integer getTypeIndex() {
        return 15;
    }

    @Override
    public void onResolveEntity(EntityHitResult rayTraceResult, Level world, @NotNull LivingEntity shooter, SpellStats spellStats, SpellContext spellContext, SpellResolver resolver) {
        this.res = resolver;
        super.onResolveEntity(rayTraceResult, world, shooter, spellStats, spellContext, resolver);
    }

    @Override
    public void onResolveBlock(BlockHitResult rayTraceResult, Level world, @NotNull LivingEntity shooter, SpellStats spellStats, SpellContext spellContext, SpellResolver resolver) {
        this.res = resolver;
        super.onResolveBlock(rayTraceResult, world, shooter, spellStats, spellContext, resolver);
    }

    @Override
    public boolean shouldResolveOnBlock(BlockHitResult target, Level level) {
        if (res == null || level.isClientSide) {
            return false;
        }

        var f = this.getFilters();
        if (f == null) {
            return false;
        }
        var filters = f.second();

        var idx = this.res.spellContext.getCurrentIndex();
        this.res.spellContext.setCurrentIndex(idx + f.first());

        try {
            return op.apply(() -> filters.first().shouldResolveOnBlock(target, level), () -> filters.second().shouldResolveOnBlock(target, level));
        } catch (Exception e) {
            if (res.spellContext.getUnwrappedCaster() instanceof Player player) {
                PortUtil.sendMessageNoSpam(player, Component.translatable("ars_controle.glyph.error.generic.error_at_position", Component.translatable(this.getLocalizationKey()), idx));
            }
            ArsControle.LOGGER.error("Failed to resolve binary filter", e);
            return false;
        }
    }

    @Override
    public boolean shouldResolveOnEntity(EntityHitResult target, Level level) {
        if (res == null || level.isClientSide) {
            return false;
        }

        var f = this.getFilters();
        if (f == null) {
            return false;
        }
        var filters = f.second();

        var idx = this.res.spellContext.getCurrentIndex();
        this.res.spellContext.setCurrentIndex(idx + f.first());

        try {
            return op.apply(() -> filters.first().shouldResolveOnEntity(target, level), () -> filters.second().shouldResolveOnEntity(target, level));
        } catch (Exception e) {
            if (res.spellContext.getUnwrappedCaster() instanceof Player player) {
                PortUtil.sendMessageNoSpam(player, Component.translatable("ars_controle.glyph.error.generic.error_at_position", Component.translatable(this.getLocalizationKey()), idx));
            }
            ArsControle.LOGGER.error("Failed to resolve binary filter", e);
            return false;
        }
    }

    @Nullable
    public Pair<Integer, Pair<IFilter, IFilter>> getFilters() {
        var idx = this.res.spellContext.getCurrentIndex();
        var spell = this.res.spell;
        var caster = res.spellContext.getUnwrappedCaster();

        if (spell.size() < idx + 2) {
            return null;
        }

        var fst = spell.get(idx);
        if (!(fst instanceof IFilter afst)) {
            return null;
        }

        var firstAugments = spell.getAugments(idx, caster);
        var skip = firstAugments.size() + 1;

        var snd = spell.get(idx + skip);
        if (!(snd instanceof IFilter asnd)) {
            return null;
        }

        if (fst instanceof FilterYLevel yfst) {
            yfst.y = (int) (this.res.spellContext.getCaster().getPosition().y - 1);
        }
        if (snd instanceof FilterYLevel ysnd) {
            ysnd.y = (int) (this.res.spellContext.getCaster().getPosition().y - 1);
        }

        if (fst instanceof FilterRandom rfst) {
            var newCtx = res.spellContext.makeChildContext();
            var newRes = res.getNewResolver(newCtx);
            var stats = new SpellStats.Builder()
                    .setAugments(firstAugments)
                    .addItemsFromEntity(caster)
                    .build(rfst, newRes.hitResult, caster.level(), caster, newCtx);
            afst = new FilterRandom.Operand(FilterRandom.calculateChance(stats.getAmpMultiplier()));
        }
        if (snd instanceof FilterRandom rsnd) {
            var augments = spell.getAugments(idx + skip, caster);
            var newCtx = res.spellContext.makeChildContext();
            var newRes = res.getNewResolver(newCtx);
            var stats = new SpellStats.Builder()
                    .setAugments(augments)
                    .addItemsFromEntity(caster)
                    .build(rsnd, newRes.hitResult, caster.level(), caster, newCtx);
            asnd = new FilterRandom.Operand(FilterRandom.calculateChance(stats.getAmpMultiplier()));
        }

        return Pair.of(skip + 1, Pair.of(afst, asnd));
    }
}
