"""
Register der Parser.

Jede Quelle nennt in ``sources.yaml`` unter ``parser`` einen Eintrag von hier.
Ein neues Portal mit eigener Logik braucht nur ein Modul mit einer
``parse(html, quelle) -> list[Action]``-Funktion und eine Zeile in diesem Dict.
"""

from __future__ import annotations

from collections.abc import Callable

from .models import Action
from .parsers import css_listing

Parser = Callable[[str, dict], list[Action]]

PARSER: dict[str, Parser] = {
    "css_listing": css_listing.parse,
}


def hole(name: str) -> Parser | None:
    return PARSER.get(name)
