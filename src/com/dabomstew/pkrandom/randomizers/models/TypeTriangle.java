package com.dabomstew.pkrandom.randomizers.models;

import com.dabomstew.pkrandom.pokemon.Pokemon;
import com.dabomstew.pkrandom.pokemon.Type;

import java.util.ArrayList;
import java.util.List;

public class TypeTriangle {
    private Type typeA;
    private Type typeB;
    private Type typeC;

    public TypeTriangle(Type typeA, Type typeB, Type typeC) {
        this.typeA = typeA;
        this.typeB = typeB;
        this.typeC = typeC;
    }

    public boolean matches(TypeTriangle other) {
        boolean isMatch = false;

        if(this.typeA == other.typeA
                && this.typeB == other.typeB
                && this.typeC == other.typeC) {
            isMatch = true;
        }
        else if (this.typeA == other.typeB
                && this.typeB == other.typeC
                && this.typeC == other.typeA) {
            isMatch = true;
        }
        else if (this.typeA == other.typeC
                && this.typeB == other.typeA
                && this.typeC == other.typeB) {
            isMatch = true;
        }

        return isMatch;
    }

    public boolean matchesAny(List<TypeTriangle> others) {
        for(TypeTriangle other: others) {
            if(this.matches(other)) {
                return true;
            }
        }

        return false;
    }

    public boolean matchesAny(Type t) {
        return t == typeA || t == typeB || t == typeC;
    }

    public List<Type> asList() {
        List<Type> typeList = new ArrayList<>();
        typeList.add(typeA);
        typeList.add(typeB);
        typeList.add(typeC);
        return typeList;
    }

    public boolean doesPokemonMatchOnlyOneType(Pokemon pokemon) {
        boolean primaryMatches = matchesAny(pokemon.primaryType);
        boolean secondaryMatches = false;
        if(pokemon.secondaryType != null) {
            secondaryMatches = matchesAny(pokemon.secondaryType);
        }

        return primaryMatches ^ secondaryMatches;
    }

    @Override
    public String toString() {
        return "[" + typeA.toString() + ", " + typeB.toString() + ", " + typeC.toString() + "]";
    }
}
