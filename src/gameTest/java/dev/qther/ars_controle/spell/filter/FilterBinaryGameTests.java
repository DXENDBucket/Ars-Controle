package dev.qther.ars_controle.spell.filter;

import com.hollingsworth.arsnouveau.api.spell.*;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.LivingCaster;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDampen;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ars_controle")
@PrefixGameTestTemplate(false)
public final class FilterBinaryGameTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void randomOperandsUseTheirOwnAugments(GameTestHelper helper) {
        var caster = helper.spawn(EntityType.COW, 2, 4, 2);
        caster.setNoAi(true);
        for (var binary : new FilterBinary[]{FilterBinary.OR, FilterBinary.XOR, FilterBinary.XNOR}) {
            var spell = new Spell(MethodSelf.INSTANCE, binary, FilterRandom.INSTANCE,
                    AugmentAmplify.INSTANCE, FilterRandom.INSTANCE, AugmentDampen.INSTANCE);
            var resolver = new SpellResolver(new SpellContext(helper.getLevel(), spell,
                    caster, LivingCaster.from(caster), ItemStack.EMPTY));
            resolver.spellContext.setCurrentIndex(2);
            var previousResolver = binary.res;
            var previousChance = FilterRandom.INSTANCE.chance;
            try {
                binary.res = resolver;
                var filters = binary.getFilters();
                helper.assertTrue(filters != null, "Binary filters must accept two random operands");
                var first = (FilterRandom.Operand) filters.second().first();
                var second = (FilterRandom.Operand) filters.second().second();
                helper.assertTrue(first.chance() == 0.75, "Amplify must affect the first operand");
                helper.assertTrue(second.chance() == 0.25, "Dampen must affect the second operand");
                helper.assertTrue(filters.first() == 3, "Consumed filter positions must stay unchanged");
                FilterRandom.INSTANCE.chance = 0.99;
                helper.assertTrue(first.chance() == 0.75 && second.chance() == 0.25,
                        "Later random casts must not change either operand");
            } finally {
                binary.res = previousResolver;
                FilterRandom.INSTANCE.chance = previousChance;
            }
        }
        helper.succeed();
    }
}
