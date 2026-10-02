#!/usr/bin/env python3
"""Builds assets/www/logos.js from the simple-icons npm package (CC0 icon data; the marks themselves remain
their owners' trademarks and are shown nominatively next to the merchant they identify).
Usage: npm pack simple-icons && tar xzf simple-icons-*.tgz && python3 tools/gen_logos.py package"""
import json, re, sys
pkg = sys.argv[1]
KEYS = """swiggy zomato netflix spotify youtube uber ubereats paytm phonepe googlepay bookmyshow airbnb starbucks mcdonalds kfc burgerking
apple applemusic jio airtel vodafone ikea zara bigbasket dunzo indigo airindia google googleplay playstation steam icloud paypal doordash lyft
tesco lidl ebay etsy shopify hbomax audible bookingdotcom expedia emirates ryanair easyjet lufthansa delta americanairlines unitedairlines
qatarairways grab gojek deliveroo justeat revolut monzo wise stripe tata anthropic""".split()
meta = {i["title"].lower(): i for i in json.load(open(f"{pkg}/data/simple-icons.json"))} if False else None
data = json.load(open(f"{pkg}/data/simple-icons.json"))
hexes = {}
for i in data:
    slug = i.get("slug") or re.sub(r"[^a-z0-9]", "", i["title"].lower())
    hexes[slug] = i["hex"]
out = {}
for k in KEYS:
    try:
        svg = open(f"{pkg}/icons/{k}.svg").read()
    except FileNotFoundError:
        continue
    d = re.search(r'<path d="([^"]+)"', svg).group(1)
    out[k] = [hexes.get(k, "888888"), d]
open("app/src/main/assets/www/logos.js", "w").write("window.LOGOS=" + json.dumps(out, separators=(",", ":")) + ";\n")
print(len(out), "logos")
