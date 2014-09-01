MooTools.More = {
    version: "1.4.0.1",
    build: "a4244edf2aa97ac8a196fc96082dd35af1abab87"
};
(function () {
    Events.Pseudos = function (a, b, e) {
        var f = function (a) {
            return {
                store: a.store ? function (b, c) {
                    a.store("_monitorEvents:" + b, c)
                } : function (b, c) {
                    (a._monitorEvents || (a._monitorEvents = {}))[b] = c
                },
                retrieve: a.retrieve ? function (b, c) {
                    return a.retrieve("_monitorEvents:" + b, c)
                } : function (b, c) {
                    return !a._monitorEvents ? c : a._monitorEvents[b] || c
                }
            }
        }, g = function (b) {
                if (-1 == b.indexOf(":") || !a) return null;
                for (var d = Slick.parse(b).expressions[0][0], e = d.pseudos, f = e.length, g = []; f--;) {
                    var n = e[f].key,
                        o = a[n];
                    null != o && g.push({
                        event: d.tag,
                        value: e[f].value,
                        pseudo: n,
                        original: b,
                        listener: o
                    })
                }
                return g.length ? g : null
            };
        return {
            addEvent: function (a, c, e) {
                var l = g(a);
                if (!l) return b.call(this, a, c, e);
                var m = f(this),
                    n = m.retrieve(a, []),
                    o = l[0].event,
                    r = Array.slice(arguments, 2),
                    q = c,
                    s = this;
                l.each(function (a) {
                    var b = a.listener,
                        c = q;
                    !1 == b ? o += ":" + a.pseudo + "(" + a.value + ")" : q = function () {
                        b.call(s, a, c, arguments, q)
                    }
                });
                n.include({
                    type: o,
                    event: c,
                    monitor: q
                });
                m.store(a, n);
                a != o && b.apply(this, [a, c].concat(r));
                return b.apply(this, [o, q].concat(r))
            },
            removeEvent: function (a,
                b) {
                if (!g(a)) return e.call(this, a, b);
                var c = f(this),
                    d = c.retrieve(a);
                if (!d) return this;
                var m = Array.slice(arguments, 2);
                e.apply(this, [a, b].concat(m));
                d.each(function (a, c) {
                    (!b || a.event == b) && e.apply(this, [a.type, a.monitor].concat(m));
                    delete d[c]
                }, this);
                c.store(a, d);
                return this
            }
        }
    };
    var a = {
        once: function (a, b, e, f) {
            b.apply(this, e);
            this.removeEvent(a.event, f).removeEvent(a.original, b)
        },
        throttle: function (a, b, e) {
            b._throttled || (b.apply(this, e), b._throttled = setTimeout(function () {
                b._throttled = !1
            }, a.value || 250))
        },
        pause: function (a,
            b, e) {
            clearTimeout(b._pause);
            b._pause = b.delay(a.value || 250, this, e)
        }
    };
    Events.definePseudo = function (b, d) {
        a[b] = d;
        return this
    };
    Events.lookupPseudo = function (b) {
        return a[b]
    };
    var b = Events.prototype;
    Events.implement(Events.Pseudos(a, b.addEvent, b.removeEvent));
    ["Request", "Fx"].each(function (a) {
        this[a] && this[a].implement(Events.prototype)
    })
})();
(function () {
    for (var a = {
        relay: !1
    }, b = ["once", "throttle", "pause"], c = b.length; c--;) a[b[c]] = Events.lookupPseudo(b[c]);
    DOMEvent.definePseudo = function (b, c) {
        a[b] = c;
        return this
    };
    b = Element.prototype;
    [Element, Window, Document].invoke("implement", Events.Pseudos(a, b.addEvent, b.removeEvent))
})();
(function () {
    var a = function (a, b) {
        var e = [];
        Object.each(b, function (b) {
            Object.each(b, function (b) {
                a.each(function (a) {
                    e.push(a + "-" + b + ("border" == a ? "-width" : ""))
                })
            })
        });
        return e
    }, b = function (a, b) {
            var e = 0;
            Object.each(b, function (b, d) {
                d.test(a) && (e += b.toInt())
            });
            return e
        };
    Element.implement({
        measure: function (a) {
            if (!this || this.offsetHeight || this.offsetWidth) return a.call(this);
            for (var b = this.getParent(), e = []; b && !b.offsetHeight && !b.offsetWidth && b != document.body;) e.push(b.expose()), b = b.getParent();
            b = this.expose();
            a = a.call(this);
            b();
            e.each(function (a) {
                a()
            });
            return a
        },
        expose: function () {
            if ("none" != this.getStyle("display")) return function () {};
            var a = this.style.cssText;
            this.setStyles({
                display: "block",
                position: "absolute",
                visibility: "hidden"
            });
            return function () {
                this.style.cssText = a
            }.bind(this)
        },
        getDimensions: function (a) {
            var a = Object.merge({
                computeSize: !1
            }, a),
                b = {
                    x: 0,
                    y: 0
                }, e = this.getParent("body");
            if (e && "none" == this.getStyle("display")) b = this.measure(function () {
                return a.computeSize ? this.getComputedSize(a) : this.getSize()
            });
            else if (e) try {
                b = a.computeSize ? this.getComputedSize(a) : this.getSize()
            } catch (f) {}
            return Object.append(b, b.x || 0 === b.x ? {
                width: b.x,
                height: b.y
            } : {
                x: b.width,
                y: b.height
            })
        },
        getComputedSize: function (c) {
            var c = Object.merge({
                styles: ["padding", "border"],
                planes: {
                    height: ["top", "bottom"],
                    width: ["left", "right"]
                },
                mode: "both"
            }, c),
                d = {}, e = {
                    width: 0,
                    height: 0
                }, f;
            "vertical" == c.mode ? (delete e.width, delete c.planes.width) : "horizontal" == c.mode && (delete e.height, delete c.planes.height);
            a(c.styles, c.planes).each(function (a) {
                d[a] =
                    this.getStyle(a).toInt()
            }, this);
            Object.each(c.planes, function (a, c) {
                var j = c.capitalize(),
                    k = this.getStyle(c);
                k == "auto" && !f && (f = this.getDimensions());
                k = d[c] = k == "auto" ? f[c] : k.toInt();
                e["total" + j] = k;
                a.each(function (a) {
                    var c = b(a, d);
                    e["computed" + a.capitalize()] = c;
                    e["total" + j] = e["total" + j] + c
                })
            }, this);
            return Object.append(e, d)
        }
    })
})();
(function (a) {
    var b = Element.Position = {
        options: {
            relativeTo: document.body,
            position: {
                x: "center",
                y: "center"
            },
            offset: {
                x: 0,
                y: 0
            }
        },
        getOptions: function (a, d) {
            d = Object.merge({}, b.options, d);
            b.setPositionOption(d);
            b.setEdgeOption(d);
            b.setOffsetOption(a, d);
            b.setDimensionsOption(a, d);
            return d
        },
        setPositionOption: function (a) {
            a.position = b.getCoordinateFromValue(a.position)
        },
        setEdgeOption: function (a) {
            var d = b.getCoordinateFromValue(a.edge);
            a.edge = d ? d : "center" == a.position.x && "center" == a.position.y ? {
                x: "center",
                y: "center"
            } : {
                x: "left",
                y: "top"
            }
        },
        setOffsetOption: function (a, b) {
            var e = {
                x: 0,
                y: 0
            }, f = a.measure(function () {
                    return document.id(this.getOffsetParent())
                }),
                g = f.getScroll();
            f && f != a.getDocument().body && (e = f.measure(function () {
                var a = this.getPosition();
                if ("fixed" == this.getStyle("position")) {
                    var b = window.getScroll();
                    a.x += b.x;
                    a.y += b.y
                }
                return a
            }), b.offset = {
                parentPositioned: f != document.id(b.relativeTo),
                x: b.offset.x - e.x + g.x,
                y: b.offset.y - e.y + g.y
            })
        },
        setDimensionsOption: function (a, b) {
            b.dimensions = a.getDimensions({
                computeSize: !0,
                styles: ["padding",
                    "border", "margin"
                ]
            })
        },
        getPosition: function (a, d) {
            var e = {}, d = b.getOptions(a, d),
                f = document.id(d.relativeTo) || document.body;
            b.setPositionCoordinates(d, e, f);
            d.edge && b.toEdge(e, d);
            var g = d.offset;
            e.left = (0 <= e.x || g.parentPositioned || d.allowNegative ? e.x : 0).toInt();
            e.top = (0 <= e.y || g.parentPositioned || d.allowNegative ? e.y : 0).toInt();
            b.toMinMax(e, d);
            (d.relFixedPosition || "fixed" == f.getStyle("position")) && b.toRelFixedPosition(f, e);
            d.ignoreScroll && b.toIgnoreScroll(f, e);
            d.ignoreMargins && b.toIgnoreMargins(e, d);
            e.left =
                Math.ceil(e.left);
            e.top = Math.ceil(e.top);
            delete e.x;
            delete e.y;
            return e
        },
        setPositionCoordinates: function (a, b, e) {
            var f = a.offset.y,
                g = a.offset.x,
                h = e == document.body ? window.getScroll() : e.getPosition(),
                j = h.y,
                h = h.x,
                k = window.getSize();
            switch (a.position.x) {
            case "left":
                b.x = h + g;
                break;
            case "right":
                b.x = h + g + e.offsetWidth;
                break;
            default:
                b.x = h + (e == document.body ? k.x : e.offsetWidth) / 2 + g
            }
            switch (a.position.y) {
            case "top":
                b.y = j + f;
                break;
            case "bottom":
                b.y = j + f + e.offsetHeight;
                break;
            default:
                b.y = j + (e == document.body ? k.y : e.offsetHeight) /
                    2 + f
            }
        },
        toMinMax: function (a, b) {
            var e = {
                left: "x",
                top: "y"
            }, f;
            ["minimum", "maximum"].each(function (g) {
                ["left", "top"].each(function (h) {
                    f = b[g] ? b[g][e[h]] : null;
                    if (null != f && ("minimum" == g ? a[h] < f : a[h] > f)) a[h] = f
                })
            })
        },
        toRelFixedPosition: function (a, b) {
            var e = window.getScroll();
            b.top += e.y;
            b.left += e.x
        },
        toIgnoreScroll: function (a, b) {
            var e = a.getScroll();
            b.top -= e.y;
            b.left -= e.x
        },
        toIgnoreMargins: function (a, b) {
            a.left += "right" == b.edge.x ? b.dimensions["margin-right"] : "center" != b.edge.x ? -b.dimensions["margin-left"] : -b.dimensions["margin-left"] +
                (b.dimensions["margin-right"] + b.dimensions["margin-left"]) / 2;
            a.top += "bottom" == b.edge.y ? b.dimensions["margin-bottom"] : "center" != b.edge.y ? -b.dimensions["margin-top"] : -b.dimensions["margin-top"] + (b.dimensions["margin-bottom"] + b.dimensions["margin-top"]) / 2
        },
        toEdge: function (a, b) {
            var e, f;
            f = b.dimensions;
            var g = b.edge;
            switch (g.x) {
            case "left":
                e = 0;
                break;
            case "right":
                e = -f.x - f.computedRight - f.computedLeft;
                break;
            default:
                e = -Math.round(f.totalWidth / 2)
            }
            switch (g.y) {
            case "top":
                f = 0;
                break;
            case "bottom":
                f = -f.y - f.computedTop -
                    f.computedBottom;
                break;
            default:
                f = -Math.round(f.totalHeight / 2)
            }
            a.x += e;
            a.y += f
        },
        getCoordinateFromValue: function (a) {
            if ("string" != typeOf(a)) return a;
            a = a.toLowerCase();
            return {
                x: a.test("left") ? "left" : a.test("right") ? "right" : "center",
                y: a.test(/upper|top/) ? "top" : a.test("bottom") ? "bottom" : "center"
            }
        }
    };
    Element.implement({
        position: function (b) {
            if (b && (null != b.x || null != b.y)) return a ? a.apply(this, arguments) : this;
            var d = this.setStyle("position", "absolute").calculatePosition(b);
            return b && b.returnPos ? d : this.setStyles(d)
        },
        calculatePosition: function (a) {
            return b.getPosition(this, a)
        }
    })
})(Element.prototype.position);
Fx.Elements = new Class({
    Extends: Fx.CSS,
    initialize: function (a, b) {
        this.elements = this.subject = $$(a);
        this.parent(b)
    },
    compute: function (a, b, c) {
        var d = {}, e;
        for (e in a) {
            var f = a[e],
                g = b[e],
                h = d[e] = {}, j;
            for (j in f) h[j] = this.parent(f[j], g[j], c)
        }
        return d
    },
    set: function (a) {
        for (var b in a)
            if (this.elements[b]) {
                var c = a[b],
                    d;
                for (d in c) this.render(this.elements[b], d, c[d], this.options.unit)
            }
        return this
    },
    start: function (a) {
        if (!this.check(a)) return this;
        var b = {}, c = {}, d;
        for (d in a)
            if (this.elements[d]) {
                var e = a[d],
                    f = b[d] = {},
                    g = c[d] = {}, h;
                for (h in e) {
                    var j = this.prepare(this.elements[d], h, e[h]);
                    f[h] = j.from;
                    g[h] = j.to
                }
            }
        return this.parent(b, c)
    }
});
Fx.Move = new Class({
    Extends: Fx.Morph,
    options: {
        relativeTo: document.body,
        position: "center",
        edge: !1,
        offset: {
            x: 0,
            y: 0
        }
    },
    start: function (a) {
        var b = this.element,
            c = b.getStyles("top", "left");
        ("auto" == c.top || "auto" == c.left) && b.setPosition(b.getPosition(b.getOffsetParent()));
        return this.parent(b.position(Object.merge({}, this.options, a, {
            returnPos: !0
        })))
    }
});
Element.Properties.move = {
    set: function (a) {
        this.get("move").cancel().setOptions(a);
        return this
    },
    get: function () {
        var a = this.retrieve("move");
        a || (a = new Fx.Move(this, {
            link: "cancel"
        }), this.store("move", a));
        return a
    }
};
Element.implement({
    move: function (a) {
        this.get("move").start(a);
        return this
    }
});
Element.implement({
    isDisplayed: function () {
        return "none" != this.getStyle("display")
    },
    isVisible: function () {
        var a = this.offsetWidth,
            b = this.offsetHeight;
        return 0 == a && 0 == b ? !1 : 0 < a && 0 < b ? !0 : "none" != this.style.display
    },
    toggle: function () {
        return this[this.isDisplayed() ? "hide" : "show"]()
    },
    hide: function () {
        var a;
        try {
            a = this.getStyle("display")
        } catch (b) {}
        return "none" == a ? this : this.store("element:_originalDisplay", a || "").setStyle("display", "none")
    },
    show: function (a) {
        if (!a && this.isDisplayed()) return this;
        a = a || this.retrieve("element:_originalDisplay") ||
            "block";
        return this.setStyle("display", "none" == a ? "block" : a)
    },
    swapClass: function (a, b) {
        return this.removeClass(a).addClass(b)
    }
});
Document.implement({
    clearSelection: function () {
        if (window.getSelection) {
            var a = window.getSelection();
            a && a.removeAllRanges && a.removeAllRanges()
        } else if (document.selection && document.selection.empty) try {
            document.selection.empty()
        } catch (b) {}
    }
});
(function () {
    var a = function (a) {
        var c = a.options.hideInputs;
        if (window.OverText) {
            var d = [null];
            OverText.each(function (a) {
                d.include("." + a.options.labelClass)
            });
            d && (c += d.join(", "))
        }
        return c ? a.element.getElements(c) : null
    };
    Fx.Reveal = new Class({
        Extends: Fx.Morph,
        options: {
            link: "cancel",
            styles: ["padding", "border", "margin"],
            transitionOpacity: !Browser.ie6,
            mode: "vertical",
            display: function () {
                return "tr" != this.element.get("tag") ? "block" : "table-row"
            },
            opacity: 1,
            hideInputs: Browser.ie ? "select, input, textarea, object, embed" : null
        },
        dissolve: function () {
            if (!this.hiding && !this.showing)
                if ("none" != this.element.getStyle("display")) {
                    this.hiding = !0;
                    this.showing = !1;
                    this.hidden = !0;
                    this.cssText = this.element.style.cssText;
                    var b = this.element.getComputedSize({
                        styles: this.options.styles,
                        mode: this.options.mode
                    });
                    this.options.transitionOpacity && (b.opacity = this.options.opacity);
                    var c = {};
                    Object.each(b, function (a, b) {
                        c[b] = [a, 0]
                    });
                    this.element.setStyles({
                        display: Function.from(this.options.display).call(this),
                        overflow: "hidden"
                    });
                    var d = a(this);
                    d && d.setStyle("visibility", "hidden");
                    this.$chain.unshift(function () {
                        if (this.hidden) {
                            this.hiding = false;
                            this.element.style.cssText = this.cssText;
                            this.element.setStyle("display", "none");
                            d && d.setStyle("visibility", "visible")
                        }
                        this.fireEvent("hide", this.element);
                        this.callChain()
                    }.bind(this));
                    this.start(c)
                } else this.callChain.delay(10, this), this.fireEvent("complete", this.element), this.fireEvent("hide", this.element);
                else "chain" == this.options.link ? this.chain(this.dissolve.bind(this)) : "cancel" == this.options.link && !this.hiding && (this.cancel(), this.dissolve());
            return this
        },
        reveal: function () {
            if (!this.showing && !this.hiding)
                if ("none" == this.element.getStyle("display")) {
                    this.hiding = !1;
                    this.showing = !0;
                    this.hidden = !1;
                    this.cssText = this.element.style.cssText;
                    var b;
                    this.element.measure(function () {
                        b = this.element.getComputedSize({
                            styles: this.options.styles,
                            mode: this.options.mode
                        })
                    }.bind(this));
                    null != this.options.heightOverride && (b.height = this.options.heightOverride.toInt());
                    null != this.options.widthOverride && (b.width = this.options.widthOverride.toInt());
                    this.options.transitionOpacity && (this.element.setStyle("opacity", 0), b.opacity = this.options.opacity);
                    var c = {
                        height: 0,
                        display: Function.from(this.options.display).call(this)
                    };
                    Object.each(b, function (a, b) {
                        c[b] = 0
                    });
                    c.overflow = "hidden";
                    this.element.setStyles(c);
                    var d = a(this);
                    d && d.setStyle("visibility", "hidden");
                    this.$chain.unshift(function () {
                        this.element.style.cssText = this.cssText;
                        this.element.setStyle("display", Function.from(this.options.display).call(this));
                        if (!this.hidden) this.showing = false;
                        d && d.setStyle("visibility",
                            "visible");
                        this.callChain();
                        this.fireEvent("show", this.element)
                    }.bind(this));
                    this.start(b)
                } else this.callChain(), this.fireEvent("complete", this.element), this.fireEvent("show", this.element);
                else "chain" == this.options.link ? this.chain(this.reveal.bind(this)) : "cancel" == this.options.link && !this.showing && (this.cancel(), this.reveal());
            return this
        },
        toggle: function () {
            "none" == this.element.getStyle("display") ? this.reveal() : this.dissolve();
            return this
        },
        cancel: function () {
            this.parent.apply(this, arguments);
            null !=
                this.cssText && (this.element.style.cssText = this.cssText);
            this.showing = this.hiding = !1;
            return this
        }
    });
    Element.Properties.reveal = {
        set: function (a) {
            this.get("reveal").cancel().setOptions(a);
            return this
        },
        get: function () {
            var a = this.retrieve("reveal");
            a || (a = new Fx.Reveal(this), this.store("reveal", a));
            return a
        }
    };
    Element.Properties.dissolve = Element.Properties.reveal;
    Element.implement({
        reveal: function (a) {
            this.get("reveal").setOptions(a).reveal();
            return this
        },
        dissolve: function (a) {
            this.get("reveal").setOptions(a).dissolve();
            return this
        },
        nix: function (a) {
            var c = Array.link(arguments, {
                destroy: Type.isBoolean,
                options: Type.isObject
            });
            this.get("reveal").setOptions(a).dissolve().chain(function () {
                this[c.destroy ? "destroy" : "dispose"]()
            }.bind(this));
            return this
        },
        wink: function () {
            var a = Array.link(arguments, {
                duration: Type.isNumber,
                options: Type.isObject
            }),
                c = this.get("reveal").setOptions(a.options);
            c.reveal().chain(function () {
                (function () {
                    c.dissolve()
                }).delay(a.duration || 2E3)
            })
        }
    })
})();
Fx.Slide = new Class({
    Extends: Fx,
    options: {
        mode: "vertical",
        wrapper: !1,
        hideOverflow: !0,
        resetHeight: !1
    },
    initialize: function (a, b) {
        a = this.element = this.subject = document.id(a);
        this.parent(b);
        var b = this.options,
            c = a.retrieve("wrapper"),
            d = a.getStyles("margin", "position", "overflow");
        b.hideOverflow && (d = Object.append(d, {
            overflow: "hidden"
        }));
        b.wrapper && (c = document.id(b.wrapper).setStyles(d));
        c || (c = (new Element("div", {
            styles: d
        })).wraps(a));
        a.store("wrapper", c).setStyle("margin", 0);
        "visible" == a.getStyle("overflow") &&
            a.setStyle("overflow", "hidden");
        this.now = [];
        this.open = !0;
        this.wrapper = c;
        this.addEvent("complete", function () {
            (this.open = 0 != c["offset" + this.layout.capitalize()]) && this.options.resetHeight && c.setStyle("height", "")
        }, !0)
    },
    vertical: function () {
        this.margin = "margin-top";
        this.layout = "height";
        this.offset = this.element.offsetHeight
    },
    horizontal: function () {
        this.margin = "margin-left";
        this.layout = "width";
        this.offset = this.element.offsetWidth
    },
    set: function (a) {
        this.element.setStyle(this.margin, a[0]);
        this.wrapper.setStyle(this.layout,
            a[1]);
        return this
    },
    compute: function (a, b, c) {
        return [0, 1].map(function (d) {
            return Fx.compute(a[d], b[d], c)
        })
    },
    start: function (a, b) {
        if (!this.check(a, b)) return this;
        this[b || this.options.mode]();
        var c = this.element.getStyle(this.margin).toInt(),
            d = this.wrapper.getStyle(this.layout).toInt(),
            e = [
                [c, d],
                [0, this.offset]
            ],
            c = [
                [c, d],
                [-this.offset, 0]
            ],
            f;
        switch (a) {
        case "in":
            f = e;
            break;
        case "out":
            f = c;
            break;
        case "toggle":
            f = 0 == d ? e : c
        }
        return this.parent(f[0], f[1])
    },
    slideIn: function (a) {
        return this.start("in", a)
    },
    slideOut: function (a) {
        return this.start("out",
            a)
    },
    hide: function (a) {
        this[a || this.options.mode]();
        this.open = !1;
        return this.set([-this.offset, 0])
    },
    show: function (a) {
        this[a || this.options.mode]();
        this.open = !0;
        return this.set([0, this.offset])
    },
    toggle: function (a) {
        return this.start("toggle", a)
    }
});
Element.Properties.slide = {
    set: function (a) {
        this.get("slide").cancel().setOptions(a);
        return this
    },
    get: function () {
        var a = this.retrieve("slide");
        a || (a = new Fx.Slide(this, {
            link: "cancel"
        }), this.store("slide", a));
        return a
    }
};
Element.implement({
    slide: function (a, b) {
        var a = a || "toggle",
            c = this.get("slide"),
            d;
        switch (a) {
        case "hide":
            c.hide(b);
            break;
        case "show":
            c.show(b);
            break;
        case "toggle":
            d = this.retrieve("slide:flag", c.open);
            c[d ? "slideOut" : "slideIn"](b);
            this.store("slide:flag", !d);
            d = !0;
            break;
        default:
            c.start(a, b)
        }
        d || this.eliminate("slide:flag");
        return this
    }
});
var Drag = new Class({
    Implements: [Events, Options],
    options: {
        snap: 6,
        unit: "px",
        grid: !1,
        style: !0,
        limit: !1,
        handle: !1,
        invert: !1,
        preventDefault: !1,
        stopPropagation: !1,
        modifiers: {
            x: "left",
            y: "top"
        }
    },
    initialize: function () {
        var a = Array.link(arguments, {
            options: Type.isObject,
            element: function (a) {
                return null != a
            }
        });
        this.element = document.id(a.element);
        this.document = this.element.getDocument();
        this.setOptions(a.options || {});
        a = typeOf(this.options.handle);
        this.handles = ("array" == a || "collection" == a ? $$(this.options.handle) :
            document.id(this.options.handle)) || this.element;
        this.mouse = {
            now: {},
            pos: {}
        };
        this.value = {
            start: {},
            now: {}
        };
        this.selection = Browser.ie ? "selectstart" : "mousedown";
        Browser.ie && !Drag.ondragstartFixed && (document.ondragstart = Function.from(!1), Drag.ondragstartFixed = !0);
        this.bound = {
            start: this.start.bind(this),
            check: this.check.bind(this),
            drag: this.drag.bind(this),
            stop: this.stop.bind(this),
            cancel: this.cancel.bind(this),
            eventStop: Function.from(!1)
        };
        this.attach()
    },
    attach: function () {
        this.handles.addEvent("mousedown",
            this.bound.start);
        return this
    },
    detach: function () {
        this.handles.removeEvent("mousedown", this.bound.start);
        return this
    },
    start: function (a) {
        var b = this.options;
        if (!a.rightClick) {
            b.preventDefault && a.preventDefault();
            b.stopPropagation && a.stopPropagation();
            this.mouse.start = a.page;
            this.fireEvent("beforeStart", this.element);
            var c = b.limit;
            this.limit = {
                x: [],
                y: []
            };
            var d, e;
            for (d in b.modifiers)
                if (b.modifiers[d]) {
                    var f = this.element.getStyle(b.modifiers[d]);
                    f && !f.match(/px$/) && (e || (e = this.element.getCoordinates(this.element.getOffsetParent())),
                        f = e[b.modifiers[d]]);
                    this.value.now[d] = b.style ? (f || 0).toInt() : this.element[b.modifiers[d]];
                    b.invert && (this.value.now[d] *= -1);
                    this.mouse.pos[d] = a.page[d] - this.value.now[d];
                    if (c && c[d])
                        for (f = 2; f--;) {
                            var g = c[d][f];
                            if (g || 0 === g) this.limit[d][f] = "function" == typeof g ? g() : g
                        }
                }
                "number" == typeOf(this.options.grid) && (this.options.grid = {
                x: this.options.grid,
                y: this.options.grid
            });
            a = {
                mousemove: this.bound.check,
                mouseup: this.bound.cancel
            };
            a[this.selection] = this.bound.eventStop;
            this.document.addEvents(a)
        }
    },
    check: function (a) {
        this.options.preventDefault &&
            a.preventDefault();
        Math.round(Math.sqrt(Math.pow(a.page.x - this.mouse.start.x, 2) + Math.pow(a.page.y - this.mouse.start.y, 2))) > this.options.snap && (this.cancel(), this.document.addEvents({
            mousemove: this.bound.drag,
            mouseup: this.bound.stop
        }), this.fireEvent("start", [this.element, a]).fireEvent("snap", this.element))
    },
    drag: function (a) {
        var b = this.options;
        b.preventDefault && a.preventDefault();
        this.mouse.now = a.page;
        for (var c in b.modifiers)
            if (b.modifiers[c]) {
                this.value.now[c] = this.mouse.now[c] - this.mouse.pos[c];
                b.invert &&
                    (this.value.now[c] *= -1);
                if (b.limit && this.limit[c])
                    if ((this.limit[c][1] || 0 === this.limit[c][1]) && this.value.now[c] > this.limit[c][1]) this.value.now[c] = this.limit[c][1];
                    else if ((this.limit[c][0] || 0 === this.limit[c][0]) && this.value.now[c] < this.limit[c][0]) this.value.now[c] = this.limit[c][0];
                b.grid[c] && (this.value.now[c] -= (this.value.now[c] - (this.limit[c][0] || 0)) % b.grid[c]);
                b.style ? this.element.setStyle(b.modifiers[c], this.value.now[c] + b.unit) : this.element[b.modifiers[c]] = this.value.now[c]
            }
        this.fireEvent("drag", [this.element, a])
    },
    cancel: function (a) {
        this.document.removeEvents({
            mousemove: this.bound.check,
            mouseup: this.bound.cancel
        });
        a && (this.document.removeEvent(this.selection, this.bound.eventStop), this.fireEvent("cancel", this.element))
    },
    stop: function (a) {
        var b = {
            mousemove: this.bound.drag,
            mouseup: this.bound.stop
        };
        b[this.selection] = this.bound.eventStop;
        this.document.removeEvents(b);
        a && this.fireEvent("complete", [this.element, a])
    }
});
Element.implement({
    makeResizable: function (a) {
        var b = new Drag(this, Object.merge({
            modifiers: {
                x: "width",
                y: "height"
            }
        }, a));
        this.store("resizer", b);
        return b.addEvent("drag", function () {
            this.fireEvent("resize", b)
        }.bind(this))
    }
});
Drag.Move = new Class({
    Extends: Drag,
    options: {
        droppables: [],
        container: !1,
        precalculate: !1,
        includeMargins: !0,
        checkDroppables: !0
    },
    initialize: function (a, b) {
        this.parent(a, b);
        a = this.element;
        this.droppables = $$(this.options.droppables);
        if ((this.container = document.id(this.options.container)) && "element" != typeOf(this.container)) this.container = document.id(this.container.getDocument().body);
        if (this.options.style) {
            if ("left" == this.options.modifiers.x && "top" == this.options.modifiers.y) {
                var c = a.getOffsetParent(),
                    d = a.getStyles("left",
                        "top");
                c && ("auto" == d.left || "auto" == d.top) && a.setPosition(a.getPosition(c))
            }
            "static" == a.getStyle("position") && a.setStyle("position", "absolute")
        }
        this.addEvent("start", this.checkDroppables, !0);
        this.overed = null
    },
    start: function (a) {
        this.container && (this.options.limit = this.calculateLimit());
        this.options.precalculate && (this.positions = this.droppables.map(function (a) {
            return a.getCoordinates()
        }));
        this.parent(a)
    },
    calculateLimit: function () {
        var a = this.element,
            b = this.container,
            c = document.id(a.getOffsetParent()) ||
                document.body,
            d = b.getCoordinates(c),
            e = {}, f = {}, g = {}, h = {};
        ["top", "right", "bottom", "left"].each(function (d) {
                e[d] = a.getStyle("margin-" + d).toInt();
                a.getStyle("border-" + d).toInt();
                f[d] = b.getStyle("margin-" + d).toInt();
                g[d] = b.getStyle("border-" + d).toInt();
                h[d] = c.getStyle("padding-" + d).toInt()
            }, this);
        var j = 0,
            k = 0,
            l = d.right - g.right - (a.offsetWidth + e.left + e.right),
            m = d.bottom - g.bottom - (a.offsetHeight + e.top + e.bottom);
        this.options.includeMargins ? (j += e.left, k += e.top) : (l += e.right, m += e.bottom);
        if ("relative" == a.getStyle("position")) {
            if (d =
                a.getCoordinates(c), d.left -= a.getStyle("left").toInt(), d.top -= a.getStyle("top").toInt(), j -= d.left, k -= d.top, "relative" != b.getStyle("position") && (j += g.left, k += g.top), l += e.left - d.left, m += e.top - d.top, b != c) j += f.left + h.left, k += (Browser.ie6 || Browser.ie7 ? 0 : f.top) + h.top
        } else j -= e.left, k -= e.top, b != c && (j += d.left + g.left, k += d.top + g.top);
        return {
            x: [j, l],
            y: [k, m]
        }
    },
    getDroppableCoordinates: function (a) {
        var b = a.getCoordinates();
        "fixed" == a.getStyle("position") && (a = window.getScroll(), b.left += a.x, b.right += a.x, b.top += a.y,
            b.bottom += a.y);
        return b
    },
    checkDroppables: function () {
        var a = this.droppables.filter(function (a, c) {
            var a = this.positions ? this.positions[c] : this.getDroppableCoordinates(a),
                d = this.mouse.now;
            return d.x > a.left && d.x < a.right && d.y < a.bottom && d.y > a.top
        }, this).getLast();
        this.overed != a && (this.overed && this.fireEvent("leave", [this.element, this.overed]), a && this.fireEvent("enter", [this.element, a]), this.overed = a)
    },
    drag: function (a) {
        this.parent(a);
        this.options.checkDroppables && this.droppables.length && this.checkDroppables()
    },
    stop: function (a) {
        this.checkDroppables();
        this.fireEvent("drop", [this.element, this.overed, a]);
        this.overed = null;
        return this.parent(a)
    }
});
Element.implement({
    makeDraggable: function (a) {
        a = new Drag.Move(this, a);
        this.store("dragger", a);
        return a
    }
});
Class.Mutators.Binds = function (a) {
    this.prototype.initialize || this.implement("initialize", function () {});
    return Array.from(a).concat(this.prototype.Binds || [])
};
Class.Mutators.initialize = function (a) {
    return function () {
        Array.from(this.Binds).each(function (a) {
            var c = this[a];
            c && (this[a] = c.bind(this))
        }, this);
        return a.apply(this, arguments)
    }
};
var Slider = new Class({
    Implements: [Events, Options],
    Binds: ["clickedElement", "draggedKnob", "scrolledElement"],
    options: {
        onTick: function (a) {
            this.setKnobPosition(a)
        },
        initialStep: 0,
        snap: !1,
        offset: 0,
        range: !1,
        wheel: !1,
        steps: 100,
        mode: "horizontal"
    },
    initialize: function (a, b, c) {
        this.setOptions(c);
        c = this.options;
        this.element = document.id(a);
        b = this.knob = document.id(b);
        this.previousChange = this.previousEnd = this.step = -1;
        var a = {}, d = {
                x: !1,
                y: !1
            };
        switch (c.mode) {
        case "vertical":
            this.axis = "y";
            this.property = "top";
            this.offset =
                "offsetHeight";
            break;
        case "horizontal":
            this.axis = "x", this.property = "left", this.offset = "offsetWidth"
        }
        this.setSliderDimensions();
        this.setRange(c.range);
        "static" == b.getStyle("position") && b.setStyle("position", "relative");
        b.setStyle(this.property, -c.offset);
        d[this.axis] = this.property;
        a[this.axis] = [-c.offset, this.full - c.offset];
        a = {
            snap: 0,
            limit: a,
            modifiers: d,
            onDrag: this.draggedKnob,
            onStart: this.draggedKnob,
            onBeforeStart: function () {
                this.isDragging = !0
            }.bind(this),
            onCancel: function () {
                this.isDragging = !1
            }.bind(this),
            onComplete: function () {
                this.isDragging = !1;
                this.draggedKnob();
                this.end()
            }.bind(this)
        };
        c.snap && this.setSnap(a);
        this.drag = new Drag(b, a);
        this.attach();
        null != c.initialStep && this.set(c.initialStep)
    },
    attach: function () {
        this.element.addEvent("mousedown", this.clickedElement);
        this.options.wheel && this.element.addEvent("mousewheel", this.scrolledElement);
        this.drag.attach();
        return this
    },
    detach: function () {
        this.element.removeEvent("mousedown", this.clickedElement).removeEvent("mousewheel", this.scrolledElement);
        this.drag.detach();
        return this
    },
    autosize: function () {
        this.setSliderDimensions().setKnobPosition(this.toPosition(this.step));
        this.drag.options.limit[this.axis] = [-this.options.offset, this.full - this.options.offset];
        this.options.snap && this.setSnap();
        return this
    },
    setSnap: function (a) {
        a || (a = this.drag.options);
        a.grid = Math.ceil(this.stepWidth);
        a.limit[this.axis][1] = this.full;
        return this
    },
    setKnobPosition: function (a) {
        this.options.snap && (a = this.toPosition(this.step));
        this.knob.setStyle(this.property, a);
        return this
    },
    setSliderDimensions: function () {
        this.full =
            this.element.measure(function () {
                this.half = this.knob[this.offset] / 2;
                return this.element[this.offset] - this.knob[this.offset] + 2 * this.options.offset
            }.bind(this));
        return this
    },
    set: function (a) {
        0 < this.range ^ a < this.min || (a = this.min);
        0 < this.range ^ a > this.max || (a = this.max);
        this.step = Math.round(a);
        return this.checkStep().fireEvent("tick", this.toPosition(this.step)).end()
    },
    setRange: function (a, b) {
        this.min = Array.pick([a[0], 0]);
        this.max = Array.pick([a[1], this.options.steps]);
        this.range = this.max - this.min;
        this.steps =
            this.options.steps || this.full;
        this.stepSize = Math.abs(this.range) / this.steps;
        this.stepWidth = this.stepSize * this.full / Math.abs(this.range);
        a && this.set(Array.pick([b, this.step]).floor(this.min).max(this.max));
        return this
    },
    clickedElement: function (a) {
        if (!(this.isDragging || a.target == this.knob)) {
            var b = 0 > this.range ? -1 : 1,
                a = a.page[this.axis] - this.element.getPosition()[this.axis] - this.half,
                a = a.limit(-this.options.offset, this.full - this.options.offset);
            this.step = Math.round(this.min + b * this.toStep(a));
            this.checkStep().fireEvent("tick",
                a).end()
        }
    },
    scrolledElement: function (a) {
        this.set(this.step + (("horizontal" == this.options.mode ? 0 > a.wheel : 0 < a.wheel) ? -1 : 1) * this.stepSize);
        a.stop()
    },
    draggedKnob: function () {
        var a = 0 > this.range ? -1 : 1,
            b = this.drag.value.now[this.axis],
            b = b.limit(-this.options.offset, this.full - this.options.offset);
        this.step = Math.round(this.min + a * this.toStep(b));
        this.checkStep()
    },
    checkStep: function () {
        var a = this.step;
        this.previousChange != a && (this.previousChange = a, this.fireEvent("change", a));
        return this
    },
    end: function () {
        var a = this.step;
        this.previousEnd !== a && (this.previousEnd = a, this.fireEvent("complete", a + ""));
        return this
    },
    toStep: function (a) {
        a = (a + this.options.offset) * this.stepSize / this.full * this.steps;
        return this.options.steps ? Math.round(a - a % this.stepSize) : a
    },
    toPosition: function (a) {
        return this.full * Math.abs(this.min - a) / (this.steps * this.stepSize) - this.options.offset
    }
});
(function () {
    this.Tips = new Class({
        Implements: [Events, Options],
        options: {
            onShow: function () {
                this.tip.setStyle("display", "block")
            },
            onHide: function () {
                this.tip.setStyle("display", "none")
            },
            title: "title",
            text: function (a) {
                return a.get("rel") || a.get("href")
            },
            showDelay: 100,
            hideDelay: 100,
            className: "tip-wrap",
            offset: {
                x: 16,
                y: 16
            },
            windowPadding: {
                x: 0,
                y: 0
            },
            fixed: !1,
            waiAria: !0
        },
        initialize: function () {
            var a = Array.link(arguments, {
                options: Type.isObject,
                elements: function (a) {
                    return null != a
                }
            });
            this.setOptions(a.options);
            a.elements &&
                this.attach(a.elements);
            this.container = new Element("div", {
                "class": "tip"
            });
            this.options.id && (this.container.set("id", this.options.id), this.options.waiAria && this.attachWaiAria())
        },
        toElement: function () {
            return this.tip ? this.tip : this.tip = (new Element("div", {
                "class": this.options.className,
                styles: {
                    position: "absolute",
                    top: 0,
                    left: 0
                }
            })).adopt(new Element("div", {
                "class": "tip-top"
            }), this.container, new Element("div", {
                "class": "tip-bottom"
            }))
        },
        attachWaiAria: function () {
            var a = this.options.id;
            this.container.set("role",
                "tooltip");
            this.waiAria || (this.waiAria = {
                show: function (b) {
                    a && b.set("aria-describedby", a);
                    this.container.set("aria-hidden", "false")
                },
                hide: function (b) {
                    a && b.erase("aria-describedby");
                    this.container.set("aria-hidden", "true")
                }
            });
            this.addEvents(this.waiAria)
        },
        detachWaiAria: function () {
            this.waiAria && (this.container.erase("role"), this.container.erase("aria-hidden"), this.removeEvents(this.waiAria))
        },
        attach: function (a) {
            $$(a).each(function (a) {
                var c = this.options.title ? "function" == typeOf(this.options.title) ? (0, this.options.title)(a) :
                    a.get(this.options.title) : "",
                    d = this.options.text ? "function" == typeOf(this.options.text) ? (0, this.options.text)(a) : a.get(this.options.text) : "";
                a.set("title", "").store("tip:native", c).retrieve("tip:title", c);
                a.retrieve("tip:text", d);
                this.fireEvent("attach", [a]);
                c = ["enter", "leave"];
                this.options.fixed || c.push("move");
                c.each(function (c) {
                    var d = a.retrieve("tip:" + c);
                    d || (d = function (d) {
                        this["element" + c.capitalize()].apply(this, [d, a])
                    }.bind(this));
                    a.store("tip:" + c, d).addEvent("mouse" + c, d)
                }, this)
            }, this);
            return this
        },
        detach: function (a) {
            $$(a).each(function (a) {
                ["enter", "leave", "move"].each(function (c) {
                    a.removeEvent("mouse" + c, a.retrieve("tip:" + c)).eliminate("tip:" + c)
                });
                this.fireEvent("detach", [a]);
                if ("title" == this.options.title) {
                    var c = a.retrieve("tip:native");
                    c && a.set("title", c)
                }
            }, this);
            return this
        },
        elementEnter: function (a, b) {
            clearTimeout(this.timer);
            this.timer = function () {
                this.container.empty();
                ["title", "text"].each(function (a) {
                    var d = b.retrieve("tip:" + a),
                        a = this["_" + a + "Element"] = (new Element("div", {
                            "class": "tip-" +
                                a
                        })).inject(this.container);
                    d && this.fill(a, d)
                }, this);
                this.show(b);
                this.position(this.options.fixed ? {
                    page: b.getPosition()
                } : a)
            }.delay(this.options.showDelay, this)
        },
        elementLeave: function (a, b) {
            clearTimeout(this.timer);
            this.timer = this.hide.delay(this.options.hideDelay, this, b);
            this.fireForParent(a, b)
        },
        setTitle: function (a) {
            this._titleElement && (this._titleElement.empty(), this.fill(this._titleElement, a));
            return this
        },
        setText: function (a) {
            this._textElement && (this._textElement.empty(), this.fill(this._textElement,
                a));
            return this
        },
        fireForParent: function (a, b) {
            (b = b.getParent()) && b != document.body && (b.retrieve("tip:enter") ? b.fireEvent("mouseenter", a) : this.fireForParent(a, b))
        },
        elementMove: function (a) {
            this.position(a)
        },
        position: function (a) {
            this.tip || document.id(this);
            var b = window.getSize(),
                c = window.getScroll(),
                d = {
                    x: this.tip.offsetWidth,
                    y: this.tip.offsetHeight
                }, e = {
                    x: "left",
                    y: "top"
                }, f = {
                    y: !1,
                    x2: !1,
                    y2: !1,
                    x: !1
                }, g = {}, h;
            for (h in e) g[e[h]] = a.page[h] + this.options.offset[h], 0 > g[e[h]] && (f[h] = !0), g[e[h]] + d[h] - c[h] > b[h] - this.options.windowPadding[h] &&
                (g[e[h]] = a.page[h] - this.options.offset[h] - d[h], f[h + "2"] = !0);
            this.fireEvent("bound", f);
            this.tip.setStyles(g)
        },
        fill: function (a, b) {
            "string" == typeof b ? a.set("html", b) : a.adopt(b)
        },
        show: function (a) {
            this.tip || document.id(this);
            this.tip.getParent() || this.tip.inject(document.body);
            this.fireEvent("show", [this.tip, a])
        },
        hide: function (a) {
            this.tip || document.id(this);
            this.fireEvent("hide", [this.tip, a])
        }
    })
})();
var IIPMooViewer = new Class({
    Extends: Events,
    version: "2.0",
    initialize: function (a, b) {
        this.source = a || alert("No element ID given to IIPMooViewer constructor");
        this.server = b.server || "/fcgi-bin/iipsrv.fcgi";
        this.render = b.render || "spiral";
        this.viewport = null;
        b.viewport && (this.viewport = {
            resolution: "undefined" == typeof b.viewport.resolution ? null : parseInt(b.viewport.resolution),
            rotation: "undefined" == typeof b.viewport.rotation ? null : parseInt(b.viewport.rotation),
            contrast: "undefined" == typeof b.viewport.contrast ?
                null : parseFloat(b.viewport.contrast),
            x: "undefined" == typeof b.viewport.x ? null : parseFloat(b.viewport.x),
            y: "undefined" == typeof b.viewport.y ? null : parseFloat(b.viewport.y)
        });
        this.images = Array(b.image.length);
        b.image || alert("Image location not set in class constructor options");
        if ("array" == typeOf(b.image))
            for (i = 0; i < b.image.length; i++) this.images[i] = {
                src: b.image[i],
                sds: "0,90",
                cnt: this.viewport && null != this.viewport.contrast ? this.viewport.contrast : 1
            };
        else this.images = [{
            src: b.image,
            sds: "0,90",
            cnt: this.viewport &&
                null != this.viewport.contrast ? this.viewport.contrast : 1
        }];
        this.loadoptions = b.load || null;
        this.credit = b.credit || null;
        this.scale = b.scale || null;
        this.enableFullscreen = "native";
        !1 == b.enableFullscreen && (this.enableFullscreen = !1);
        "page" == b.enableFullscreen && (this.enableFullscreen = "page");
        this.fullscreen = null;
        !1 != this.enableFullscreen && (this.fullscreen = {
            isFullscreen: !1,
            targetsize: {},
            eventChangeName: null,
            enter: null,
            exit: null
        });
        this.disableContextMenu = !0;
        this.showNavWindow = !1 == b.showNavWindow ? !1 : !0;
        this.showNavButtons = !1 == b.showNavButtons ? !1 : !0;
        this.navWinSize = b.navWinSize || 0.2;
        this.winResize = !1 == b.winResize ? !1 : !0;
        this.prefix = b.prefix || "images/";
        switch (b.protocol) {
        case "zoomify":
            this.protocol = new Protocols.Zoomify;
            break;
        case "deepzoom":
            this.protocol = new Protocols.DeepZoom;
            break;
        case "djatoka":
            this.protocol = new Protocols.Djatoka;
            break;
        default:
            this.protocol = new Protocols.IIP
        }
        this.preload = !0 == b.preload ? !0 : !1;
        this.effects = !1;
        this.annotations = "function" == typeof this.initAnnotationTips && b.annotations ? b.annotations : null;
        this.click = b.click || null;
        this.max_size = {};
        this.navWin = {
            w: 0,
            h: 0
        };
        this.hei = this.wid = this.opacity = 0;
        this.resolutions;
        this.num_resolutions = 0;
        this.view = {
            x: 0,
            y: 0,
            w: this.wid,
            h: this.hei,
            res: 0,
            rotation: 0
        };
        this.navpos = {};
        this.tileSize = {};
        this.tiles = [];
        this.nTilesToLoad = this.nTilesLoaded = 0;
        this.CSSprefix = "";
        Browser.firefox ? this.CSSprefix = "-moz-" : Browser.chrome || Browser.safari || Browser.Platform.ios ? this.CSSprefix = "-webkit-" : Browser.opera ? this.CSSprefix = "-o-" : Browser.ie && (this.CSSprefix = "ms-");
        window.addEvent("domready",
            this.load.bind(this))
    },
    requestImages: function () {
        this.canvas.setStyle("cursor", "wait");
        this.annotations && this.destroyAnnotations();
        if (!Browser.buggy) {
            var a = (this.wid > this.view.w ? Math.round(this.view.x + this.view.w / 2) : Math.round(this.wid / 2)) + "px",
                b = (this.hei > this.view.h ? Math.round(this.view.y + this.view.h / 2) : Math.round(this.hei / 2)) + "px";
            this.canvas.setStyle(this.CSSprefix + "transform-origin", a + " " + b)
        }
        this.loadGrid();
        this.annotations && (this.createAnnotations(), this.annotationTip && this.annotationTip.attach(this.canvas.getChildren("div.annotation")))
    },
    loadGrid: function () {
        var a = this.preload ? 1 : 0,
            b = Math.floor(this.view.x / this.tileSize.w) - a,
            c = Math.floor(this.view.y / this.tileSize.h) - a;
        0 > b && (b = 0);
        0 > c && (c = 0);
        var d = this.view.w;
        this.wid < this.view.w && (d = this.wid);
        var e = Math.ceil((d + this.view.x) / this.tileSize.w - 1) + a,
            d = this.view.h;
        this.hei < this.view.h && (d = this.hei);
        var f = Math.ceil((d + this.view.y) / this.tileSize.h - 1) + a,
            a = Math.ceil(this.wid / this.tileSize.h),
            d = Math.ceil(this.hei / this.tileSize.h);
        e >= a && (e = a - 1);
        f >= d && (f = d - 1);
        var g, h;
        h = d = 0;
        var j = b + Math.round((e -
            b) / 2),
            k = c + Math.round((f - c) / 2),
            l = Array((e - b) * (e - b)),
            m = Array((e - b) * (e - b));
        m.empty();
        var n = 0;
        for (g = c; g <= f; g++)
            for (c = b; c <= e; c++) l[n] = {}, l[n].n = "spiral" == this.render ? Math.abs(k - g) * Math.abs(k - g) + Math.abs(j - c) * Math.abs(j - c) : Math.random(), l[n].x = c, l[n].y = g, n++, d = c + g * a, m.push(d);
        this.nTilesLoaded = 0;
        this.nTilesToLoad = n * this.images.length;
        this.canvas.get("morph").cancel();
        var o = this;
        this.canvas.getChildren("img").each(function (a) {
            var b = parseInt(a.retrieve("tile"));
            if (!m.contains(b)) {
                a.destroy();
                o.tiles.erase(b)
            }
        });
        l.sort(function (a, b) {
            return a.n - b.n
        });
        for (e = 0; e < n; e++)
            if (c = l[e].x, g = l[e].y, d = c + g * a, this.tiles.contains(d)) this.nTilesLoaded += this.images.length, this.showNavWindow && this.refreshLoadBar(), this.nTilesLoaded >= this.nTilesToLoad && this.canvas.setStyle("cursor", "move");
            else
                for (h = 0; h < this.images.length; h++) b = new Element("img", {
                    "class": "layer" + h,
                    styles: {
                        left: c * this.tileSize.w,
                        top: g * this.tileSize.h
                    }
                }), this.effects && b.setStyle("opacity", 0.1), b.inject(this.canvas), f = this.protocol.getTileURL(this.server, this.images[h].src,
                    this.view.res, this.images[h].sds || "0,90", this.images[h].cnt, d, c, g), b.addEvents({
                    load: function (a, b) {
                        this.effects && a.setStyle("opacity", 1);
                        if (!a.width || !a.height) a.fireEvent("error");
                        else {
                            this.nTilesLoaded++;
                            this.showNavWindow && this.refreshLoadBar();
                            this.nTilesLoaded >= this.nTilesToLoad && this.canvas.setStyle("cursor", "move");
                            this.tiles.push(b)
                        }
                    }.bind(this, b, d),
                    error: function () {
                        this.removeEvents("error");
                        this.set("src", this.src + "?" + Date.now())
                    }
                }), b.set("src", f), b.store("tile", d);
        1 < this.images.length &&
            this.canvas.getChildren("img.layer" + (h - 1)).setStyle("opacity", this.opacity)
    },
    getRegionURL: function () {
        var a = this.resolutions[this.view.res].w,
            b = this.resolutions[this.view.res].h;
        return this.protocol.getRegionURL(this.server, this.images[0].src, {
            x: this.view.x / a,
            y: this.view.y / b,
            w: this.view.w / a,
            h: this.view.h / b
        }, a)
    },
    key: function (a) {
        var b = new DOMEvent(a),
            c = Math.round(this.view.w / 4);
        switch (a.code) {
        case 37:
            this.nudge(-c, 0);
            IIPMooViewer.sync && IIPMooViewer.windows(this).each(function (a) {
                a.nudge(-c, 0)
            });
            b.preventDefault();
            break;
        case 38:
            this.nudge(0, -c);
            IIPMooViewer.sync && IIPMooViewer.windows(this).each(function (a) {
                a.nudge(0, -c)
            });
            b.preventDefault();
            break;
        case 39:
            this.nudge(c, 0);
            IIPMooViewer.sync && IIPMooViewer.windows(this).each(function (a) {
                a.nudge(c, 0)
            });
            b.preventDefault();
            break;
        case 40:
            this.nudge(0, c);
            IIPMooViewer.sync && IIPMooViewer.windows(this).each(function (a) {
                a.nudge(0, c)
            });
            b.preventDefault();
            break;
        case 107:
            a.control || (this.zoomIn(), IIPMooViewer.sync && IIPMooViewer.windows(this).each(function (a) {
                a.zoomIn()
            }), b.preventDefault());
            break;
        case 109:
            a.control || (this.zoomOut(), IIPMooViewer.sync && IIPMooViewer.windows(this).each(function (a) {
                a.zoomOut()
            }));
            break;
        case 189:
            a.control || this.zoomOut();
            break;
        case 72:
            this.toggleNavigationWindow();
            break;
        case 82:
            if (!a.control) {
                var d = this.view.rotation,
                    d = a.shift ? d - 45 : d + 45;
                this.rotate(d);
                IIPMooViewer.sync && IIPMooViewer.windows(this).each(function (a) {
                    a.rotate(d)
                })
            }
            break;
        case 65:
            this.annotations && this.toggleAnnotations();
            break;
        case 27:
            this.fullscreen && this.fullscreen.isFullscreen && (IIPMooViewer.sync ||
                this.toggleFullScreen());
            this.container.getElement("div.info").fade("out");
            break;
        case 70:
            IIPMooViewer.sync || this.toggleFullScreen()
        }
    },
    rotate: function (a) {
        Browser.buggy || (this.view.rotation = a, this.canvas.setStyle(this.CSSprefix + "transform", "rotate(" + a + "deg)"))
    },
    toggleFullScreen: function () {
        var a, b, c, d;
        if (!1 != this.enableFullscreen && (this.fullscreen.isFullscreen ? (a = this.fullscreen.targetsize.pos.x, b = this.fullscreen.targetsize.pos.y, c = this.fullscreen.targetsize.size.x, d = this.fullscreen.targetsize.size.y,
            p = this.fullscreen.targetsize.position, this.fullscreen.exit && this.fullscreen.isFullscreen && this.fullscreen.exit.call(document)) : (this.fullscreen.targetsize = {
            pos: {
                x: this.container.style.left,
                y: this.container.style.top
            },
            size: {
                x: this.container.style.width,
                y: this.container.style.height
            },
            position: this.container.style.position
        }, b = a = 0, d = c = "100%", p = "absolute", this.fullscreen.enter && !this.fullscreen.isFullscreen && this.fullscreen.enter.call(this.container)), !this.fullscreen.enter)) this.container.setStyles({
            left: a,
            top: b,
            width: c,
            height: d,
            position: p
        }), this.fullscreen.isFullscreen = !this.fullscreen.isFullscreen, this.fullscreen.isFullscreen ? this.showPopUp(IIPMooViewer.lang.exitFullscreen) : this.container.getElements("div.message").destroy(), this.reload()
    },
    toggleNavigationWindow: function () {
        this.navcontainer && this.navcontainer.get("reveal").toggle()
    },
    showPopUp: function (a) {
        var b = (new Element("div", {
            "class": "message",
            html: a
        })).inject(this.container);
        (Browser.buggy ? function () {
            b.destroy()
        } : function () {
            b.fade("out").get("tween").chain(function () {
                b.destroy()
            })
        }).delay(3E3)
    },
    scrollNavigation: function (a) {
        this.zone.get("morph").cancel();
        this.canvas.get("morph").cancel();
        var b = 0,
            c = 0,
            d = this.zone.getSize(),
            e = d.x,
            d = d.y;
        if (a.event) {
            a.stop();
            var f = this.zone.getParent().getPosition(),
                b = a.page.x - f.x - Math.floor(e / 2),
                c = a.page.y - f.y - Math.floor(d / 2)
        } else if (b = a.offsetLeft, c = a.offsetTop - 10, 3 > Math.abs(b - this.navpos.x) && 3 > Math.abs(c - this.navpos.y)) return;
        b > this.navWin.w - e && (b = this.navWin.w - e);
        c > this.navWin.h - d && (c = this.navWin.h - d);
        0 > b && (b = 0);
        0 > c && (c = 0);
        b = Math.round(b * this.wid / this.navWin.w);
        c = Math.round(c * this.hei / this.navWin.h);
        (e = Math.abs(b - this.view.x) < this.view.w / 2 && Math.abs(c - this.view.y) < this.view.h / 2 && 0 == this.view.rotation) ? this.canvas.morph({
            left: this.wid > this.view.w ? -b : Math.round((this.view.w - this.wid) / 2),
            top: this.hei > this.view.h ? -c : Math.round((this.view.h - this.hei) / 2)
        }) : this.canvas.setStyles({
            left: this.wid > this.view.w ? -b : Math.round((this.view.w - this.wid) / 2),
            top: this.hei > this.view.h ? -c : Math.round((this.view.h - this.hei) / 2)
        });
        this.view.x = b;
        this.view.y = c;
        e || this.requestImages();
        a.event && this.positionZone();
        IIPMooViewer.sync && IIPMooViewer.windows(this).each(function (a) {
            a.moveTo(b, c)
        })
    },
    scroll: function () {
        var a, b;
        a = this.canvas.getStyle("left").toInt();
        b = this.canvas.getStyle("top").toInt();
        var c = -a,
            d = -b;
        this.moveTo(c, d);
        IIPMooViewer.sync && IIPMooViewer.windows(this).each(function (a) {
            a.moveTo(c, d)
        })
    },
    checkBounds: function (a, b) {
        a > this.wid - this.view.w && (a = this.wid - this.view.w);
        b > this.hei - this.view.h && (b = this.hei - this.view.h);
        if (0 > a || this.wid < this.view.w) a = 0;
        if (0 > b || this.hei < this.view.h) b =
            0;
        this.view.x = a;
        this.view.y = b
    },
    moveTo: function (a, b) {
        a == this.view.x && b == this.view.y || (this.checkBounds(a, b), this.canvas.setStyles({
            left: this.wid > this.view.w ? -this.view.x : Math.round((this.view.w - this.wid) / 2),
            top: this.hei > this.view.h ? -this.view.y : Math.round((this.view.h - this.hei) / 2)
        }), this.requestImages(), this.positionZone())
    },
    nudge: function (a, b) {
        this.checkBounds(this.view.x + a, this.view.y + b);
        this.canvas.morph({
            left: this.wid > this.view.w ? -this.view.x : Math.round((this.view.w - this.wid) / 2),
            top: this.hei > this.view.h ? -this.view.y : Math.round((this.view.h - this.hei) / 2)
        });
        this.positionZone()
    },
    zoom: function (a) {
        a = new DOMEvent(a);
        a.stop();
        var b = 1,
            b = a.wheel && 0 > a.wheel ? -1 : a.shift ? -1 : 1;
        if (!(1 == b && this.view.res >= this.num_resolutions - 1 || -1 == b && 0 >= this.view.res)) {
            if (a.target) {
                var c;
                c = a.target.get("class");
                if ("zone" != c & "navimage" != c) c = this.canvas.getPosition(), this.view.x = a.page.x - c.x - Math.floor(this.view.w / 2), this.view.y = a.page.y - c.y - Math.floor(this.view.h / 2);
                else {
                    c = this.zone.getParent().getPosition();
                    var d = this.zone.getParent().getSize(),
                        e = this.zone.getSize();
                    this.view.x = Math.round((a.page.x - c.x - e.x / 2) * this.wid / d.x);
                    this.view.y = Math.round((a.page.y - c.y - e.y / 2) * this.hei / d.y)
                } if (IIPMooViewer.sync) {
                    var f = this.view.x,
                        g = this.view.y;
                    IIPMooViewer.windows(this).each(function (a) {
                        a.view.x = f;
                        a.view.y = g
                    })
                }
            } - 1 == b ? this.zoomOut() : this.zoomIn();
            IIPMooViewer.sync && IIPMooViewer.windows(this).each(function (a) {
                -1 == b ? a.zoomOut() : a.zoomIn()
            })
        }
    },
    zoomIn: function () {
        this.view.res < this.num_resolutions - 1 && this.zoomTo(this.view.res + 1)
    },
    zoomOut: function () {
        0 < this.view.res &&
            this.zoomTo(this.view.res - 1)
    },
    zoomTo: function (a) {
        if (a != this.view.res && a <= this.num_resolutions - 1 && 0 <= a) {
            var b = Math.pow(2, a - this.view.res),
                c, d;
            a > this.view.res ? (c = this.resolutions[this.view.res].w > this.view.w ? this.view.w * (b - 1) / 2 : this.resolutions[a].w / 2 - this.view.w / 2, d = this.resolutions[this.view.res].h > this.view.h ? this.view.h * (b - 1) / 2 : this.resolutions[a].h / 2 - this.view.h / 2) : (c = -this.view.w * (1 - b) / 2, d = -this.view.h * (1 - b) / 2);
            this.view.x = Math.round(b * this.view.x + c);
            this.view.y = Math.round(b * this.view.y + d);
            this.view.res =
                a;
            this._zoom()
        }
    },
    _zoom: function () {
        this.wid = this.resolutions[this.view.res].w;
        this.hei = this.resolutions[this.view.res].h;
        this.view.x + this.view.w > this.wid && (this.view.x = this.wid - this.view.w);
        0 > this.view.x && (this.view.x = 0);
        this.view.y + this.view.h > this.hei && (this.view.y = this.hei - this.view.h);
        0 > this.view.y && (this.view.y = 0);
        this.canvas.setStyles({
            left: this.wid > this.view.w ? -this.view.x : Math.round((this.view.w - this.wid) / 2),
            top: this.hei > this.view.h ? -this.view.y : Math.round((this.view.h - this.hei) / 2),
            width: this.wid,
            height: this.hei
        });
        this.constrain();
        this.canvas.getChildren("img").destroy();
        this.tiles.empty();
        this.requestImages();
        this.positionZone();
        this.scale && this.updateScale()
    },
    calculateNavSize: function () {
        var a = this.view.w * this.navWinSize;
        this.max_size.w > 2 * this.max_size.h && (a = this.view.w / 2);
        this.max_size.h / this.max_size.w * a > 0.5 * this.view.h && (a = Math.round(0.5 * this.view.h * this.max_size.w / this.max_size.h));
        this.navWin.w = a;
        this.navWin.h = Math.round(this.max_size.h / this.max_size.w * a)
    },
    calculateSizes: function () {
        var a =
            this.container.getSize();
        this.view.x = -1;
        this.view.y = -1;
        this.view.w = a.x;
        this.view.h = a.y;
        this.calculateNavSize();
        this.view.res = this.num_resolutions;
        var a = this.max_size.w,
            b = this.max_size.h;
        this.resolutions = Array(this.num_resolutions);
        this.resolutions.push({
            w: a,
            h: b
        });
        this.view.res = 0;
        for (var c = 1; c < this.num_resolutions; c++) a = Math.floor(a / 2), b = Math.floor(b / 2), this.resolutions.push({
            w: a,
            h: b
        }), a < this.view.w && b < this.view.h && this.view.res++;
        this.view.res -= 1;
        0 > this.view.res && (this.view.res = 0);
        this.view.res >=
            this.num_resolutions && (this.view.res = this.num_resolutions - 1);
        this.resolutions.reverse();
        this.wid = this.resolutions[this.view.res].w;
        this.hei = this.resolutions[this.view.res].h
    },
    setCredit: function (a) {
        this.container.getElement("div.credit").set("html", a)
    },
    createWindows: function () {
        this.container = document.id(this.source);
        this.container.addClass("iipmooviewer");
        var a = this;
        "native" == this.enableFullscreen && ((document.documentElement.requestFullscreen ? (this.fullscreen.eventChangeName = "fullscreenchange", this.enter =
                this.container.requestFullscreen, this.exit = document.documentElement.cancelFullScreen) : document.mozCancelFullScreen ? (this.fullscreen.eventChangeName = "mozfullscreenchange", this.fullscreen.enter = this.container.mozRequestFullScreen, this.fullscreen.exit = document.documentElement.mozCancelFullScreen) : document.webkitCancelFullScreen && (this.fullscreen.eventChangeName = "webkitfullscreenchange", this.fullscreen.enter = this.container.webkitRequestFullScreen, this.fullscreen.exit = document.documentElement.webkitCancelFullScreen),
            this.fullscreen.enter) ? document.addEvent(this.fullscreen.eventChangeName, function () {
            a.fullscreen.isFullscreen = !a.fullscreen.isFullscreen;
            a.reload()
        }) : "100%" == this.container.getStyle("width") && "100%" == this.container.getStyle("height") && (this.enableFullscreen = !1));
        (new Element("div", {
            "class": "info",
            styles: {
                opacity: 0
            },
            events: {
                click: function () {
                    this.fade("out")
                }
            },
            html: '<div><div><h2><a href="http://iipimage.sourceforge.net"><img src="' + this.prefix + 'iip.32x32.png"/></a>IIPMooViewer</h2>IIPImage HTML5 Ajax High Resolution Image Viewer - Version ' +
                this.version + "<br/><ul><li>" + IIPMooViewer.lang.navigate + "</li><li>" + IIPMooViewer.lang.zoomIn + "</li><li>" + IIPMooViewer.lang.zoomOut + "</li><li>" + IIPMooViewer.lang.rotate + "</li><li>" + IIPMooViewer.lang.fullscreen + "<li>" + IIPMooViewer.lang.annotations + "</li><li>" + IIPMooViewer.lang.navigation + "</li></ul><br/>" + IIPMooViewer.lang.more + ' <a href="http://iipimage.sourceforge.net">http://iipimage.sourceforge.net</a></div></div>'
        })).inject(this.container);
        this.canvas = new Element("div", {
            "class": "canvas",
            morph: {
                transition: Fx.Transitions.Quad.easeInOut,
                onComplete: function () {
                    a.requestImages()
                }
            }
        });
        this.touch = new Drag(this.canvas, {
            onComplete: this.scroll.bind(this)
        });
        this.canvas.inject(this.container);
        this.canvas.addEvents({
            "mousewheel:throttle(75)": this.zoom.bind(this),
            dblclick: this.zoom.bind(this),
            mousedown: function (a) {
                (new DOMEvent(a)).stop()
            }
        });
        this.annotations && this.initAnnotationTips();
        this.disableContextMenu && this.container.addEvent("contextmenu", function (b) {
            (new DOMEvent(b)).stop();
            a.container.getElement("div.info").fade(0.95);
            return !1
        });
        if (this.click) {
            var b =
                this.click.bind(this);
            this.canvas.addEvent("mouseup", b);
            this.touch.addEvents({
                start: function () {
                    a.canvas.removeEvents("mouseup")
                },
                complete: function () {
                    a.canvas.addEvent("mouseup", b)
                }
            })
        }
        this.container.addEvents({
            keydown: this.key.bind(this),
            mouseenter: function () {
                this.set("tabindex", 0);
                this.focus()
            },
            mouseleave: function () {
                this.erase("tabindex");
                this.blur()
            },
            mousewheel: function (a) {
                a.preventDefault()
            }
        });
        if (Browser.Platform.ios || Browser.Platform.android) this.container.addEvent("touchmove", function (a) {
            a.preventDefault()
        }),
        document.body.addEvents({
            touchmove: function (a) {
                a.preventDefault()
            },
            orientationchange: function () {
                a.container.setStyles({
                    width: "100%",
                    height: "100%"
                });
                this.reflow.delay(500, this)
            }.bind(this)
        }), this.canvas.addEvents({
            touchstart: function (b) {
                b.preventDefault();
                if (1 == b.touches.length) {
                    var c = a.canvas.retrieve("taptime") || 0,
                        f = Date.now();
                    a.canvas.store("taptime", f);
                    a.canvas.store("tapstart", 1);
                    500 > f - c ? (a.canvas.eliminate("taptime"), a.zoomIn()) : (c = a.canvas.getPosition(a.container), a.touchstart = {
                        x: b.touches[0].pageX -
                            c.x,
                        y: b.touches[0].pageY - c.y
                    })
                }
            },
            touchmove: function (b) {
                1 == b.touches.length && (a.view.x = a.touchstart.x - b.touches[0].pageX, a.view.y = a.touchstart.y - b.touches[0].pageY, a.view.x > a.wid - a.view.w && (a.view.x = a.wid - a.view.w), a.view.y > a.hei - a.view.h && (a.view.y = a.hei - a.view.h), 0 > a.view.x && (a.view.x = 0), 0 > a.view.y && (a.view.y = 0), a.canvas.setStyles({
                    left: a.wid > a.view.w ? -a.view.x : Math.round((a.view.w - a.wid) / 2),
                    top: a.hei > a.view.h ? -a.view.y : Math.round((a.view.h - a.hei) / 2)
                }));
                if (2 == b.touches.length) {
                    var c = Math.round((b.touches[0].pageX +
                        b.touches[1].pageX) / 2) + a.view.x,
                        b = Math.round((b.touches[0].pageY + b.touches[1].pageY) / 2) + a.view.y;
                    a.canvas.setStyle(this.CSSprefix + "transform-origin", c + "px," + b + "px")
                }
            },
            touchend: function () {
                1 == a.canvas.retrieve("tapstart") && (a.canvas.eliminate("tapstart"), a.requestImages(), a.positionZone())
            },
            gesturestart: function (b) {
                b.preventDefault();
                a.canvas.store("tapstart", 1)
            },
            gesturechange: function (a) {
                a.preventDefault()
            },
            gestureend: function (b) {
                if (1 == a.canvas.retrieve("tapstart"))
                    if (a.canvas.eliminate("tapstart"),
                        0.1 < Math.abs(1 - b.scale)) 1 < b.scale ? a.zoomIn() : a.zoomOut();
                    else if (10 < Math.abs(b.rotation)) {
                    var c = a.view.rotation,
                        c = 0 < b.rotation ? c + 45 : c - 45;
                    a.rotate(c)
                }
            }
        });
        var c = (new Element("img", {
            src: this.prefix + "iip.32x32.png",
            "class": "logo",
            title: IIPMooViewer.lang.help,
            events: {
                click: function () {
                    a.container.getElement("div.info").fade(0.95)
                },
                mousedown: function (a) {
                    (new DOMEvent(a)).stop()
                }
            }
        })).inject(this.container);
        Browser.Platform.ios && window.navigator.standalone && c.setStyle("top", 15);
        this.credit && (new Element("div", {
            "class": "credit",
            html: this.credit,
            events: {
                mouseover: function () {
                    this.fade([0.6, 0.9])
                },
                mouseout: function () {
                    this.fade(0.6)
                }
            }
        })).inject(this.container);
        this.scale && (c = (new Element("div", {
            "class": "scale",
            title: IIPMooViewer.lang.scale,
            html: '<div class="ruler"></div><div class="label"></div>'
        })).inject(this.container), c.makeDraggable({
            container: this.container
        }), c.getElement("div.ruler").set("tween", {
            transition: Fx.Transitions.Quad.easeInOut
        }));
        this.calculateSizes();
        this.createNavigationWindow();
        this.annotations &&
            this.createAnnotations();
        if (!Browser.Platform.ios && !Browser.Platform.android) {
            c = "img.logo, div.toolbar, div.scale";
            if (Browser.ie8 || Browser.ie7) c = "img.logo, div.toolbar";
            new Tips(c, {
                className: "tip",
                onShow: function (a) {
                    a.setStyles({
                        opacity: 0,
                        display: "block"
                    }).fade(0.9)
                },
                onHide: function (a) {
                    a.fade("out").get("tween").chain(function () {
                        a.setStyle("display", "none")
                    })
                }
            })
        }
        this.viewport && typeof ("undefined" != this.viewport.resolution) && "undefined" == typeof this.resolutions[this.viewport.resolution] && (this.viewport.resolution =
            null);
        this.viewport && null != this.viewport.resolution && (this.view.res = this.viewport.resolution, this.wid = this.resolutions[this.view.res].w, this.hei = this.resolutions[this.view.res].h, this.touch.options.limit = {
            x: [this.view.w - this.wid, 0],
            y: [this.view.h - this.hei, 0]
        });
        this.viewport && null != this.viewport.x && null != this.viewport.y ? this.moveTo(this.viewport.x * this.wid, this.viewport.y * this.hei) : this.recenter();
        this.canvas.setStyles({
            width: this.wid,
            height: this.hei
        });
        this.requestImages();
        this.positionZone();
        this.scale &&
            this.updateScale();
        this.viewport && null != this.viewport.rotation && this.rotate(this.viewport.rotation);
        this.winResize && window.addEvent("resize", this.reflow.bind(this));
        this.fireEvent("load")
    },
    createNavigationWindow: function () {
        if (this.showNavWindow || this.showNavButtons) {
            this.navcontainer = new Element("div", {
                "class": "navcontainer",
                styles: {
                    position: "absolute",
                    width: this.navWin.w
                }
            });
            Browser.Platform.ios && window.navigator.standalone && this.navcontainer.setStyle("top", 20);
            var a = new Element("div", {
                "class": "toolbar",
                events: {
                    dblclick: function (a) {
                        a.getElement("div.navbuttons").get("slide").toggle()
                    }.pass(this.container)
                }
            });
            a.store("tip:text", IIPMooViewer.lang.drag);
            a.inject(this.navcontainer);
            if (this.showNavWindow) {
                var b = new Element("div", {
                    "class": "navwin",
                    styles: {
                        height: this.navWin.h
                    }
                });
                b.inject(this.navcontainer);
                (new Element("img", {
                    "class": "navimage",
                    src: this.protocol.getThumbnailURL(this.server, this.images[0].src, this.navWin.w),
                    events: {
                        click: this.scrollNavigation.bind(this),
                        "mousewheel:throttle(75)": this.zoom.bind(this),
                        mousedown: function (a) {
                            (new DOMEvent(a)).stop()
                        }
                    }
                })).inject(b);
                this.zone = new Element("div", {
                    "class": "zone",
                    morph: {
                        duration: 500,
                        transition: Fx.Transitions.Quad.easeInOut
                    },
                    events: {
                        "mousewheel:throttle(75)": this.zoom.bind(this),
                        dblclick: this.zoom.bind(this)
                    }
                });
                this.zone.inject(b)
            }
            if (this.showNavButtons) {
                var c = new Element("div", {
                    "class": "navbuttons"
                }),
                    d = this.prefix;
                ["reset", "zoomIn", "zoomOut"].each(function (a) {
                        (new Element("img", {
                            src: d + a + (Browser.buggy ? ".png" : ".svg"),
                            "class": a,
                            events: {
                                error: function () {
                                    this.removeEvents("error");
                                    this.src = this.src.replace(".svg", ".png")
                                }
                            }
                        })).inject(c)
                    });
                c.inject(this.navcontainer);
                c.set("slide", {
                    duration: 300,
                    transition: Fx.Transitions.Quad.easeInOut,
                    mode: "vertical"
                });
                c.getElement("img.zoomIn").addEvent("click", function () {
                    IIPMooViewer.windows(this).each(function (a) {
                        a.zoomIn()
                    });
                    this.zoomIn()
                }.bind(this));
                c.getElement("img.zoomOut").addEvent("click", function () {
                    IIPMooViewer.windows(this).each(function (a) {
                        a.zoomOut()
                    });
                    this.zoomOut()
                }.bind(this));
                c.getElement("img.reset").addEvent("click", function () {
                    IIPMooViewer.windows(this).each(function (a) {
                        a.reload()
                    });
                    this.reload()
                }.bind(this))
            }
            this.showNavWindow && (new Element("div", {
                "class": "loadBarContainer",
                html: '<div class="loadBar"></div>',
                styles: {
                    width: this.navWin.w - 2
                },
                tween: {
                    duration: 1E3,
                    transition: Fx.Transitions.Sine.easeOut,
                    link: "cancel"
                }
            })).inject(this.navcontainer);
            this.navcontainer.inject(this.container);
            this.showNavWindow && this.zone.makeDraggable({
                container: this.navcontainer.getElement("div.navwin"),
                onStart: function () {
                    var a = this.zone.getPosition();
                    this.navpos = {
                        x: a.x,
                        y: a.y - 10
                    };
                    this.zone.get("morph").cancel()
                }.bind(this),
                onComplete: this.scrollNavigation.bind(this)
            });
            this.navcontainer.makeDraggable({
                container: this.container,
                handle: a
            })
        }
    },
    refreshLoadBar: function () {
        var a = this.nTilesLoaded / this.nTilesToLoad * this.navWin.w,
            b = this.navcontainer.getElement("div.loadBarContainer"),
            c = b.getElement("div.loadBar");
        c.setStyle("width", a);
        c.set("html", IIPMooViewer.lang.loading + "&nbsp;:&nbsp;" + Math.round(100 * (this.nTilesLoaded / this.nTilesToLoad)) + "%");
        "0.85" != b.style.opacity && b.setStyles({
            visibility: "visible",
            opacity: 0.85
        });
        this.nTilesLoaded >=
            this.nTilesToLoad && b.fade("out")
    },
    updateScale: function () {
        var a = [1.0E-12, 1.0E-9, 1.0E-6, 0.0010, 0.01, 1, 1E3],
            b = [1, 2, 5, 10, 50, 100],
            c = 1E3 * this.scale * this.wid / this.max_size.w,
            d, e;
        d = 0;
        a: for (; d < a.length; d++)
            for (e = 0; e < b.length; e++)
                if (a[d] * b[e] * c > this.view.w / 20) break a;d >= a.length && (d = a.length - 1);
        e >= b.length && (e = b.length - 1);
        var f = b[e] + "p,n,&#181;,m,c,,k".split(",")[d] + "m",
            c = c * a[d] * b[e];
        this.container.getElement("div.scale div.ruler").tween("width", c);
        this.container.getElement("div.scale div.label").set("html",
            f)
    },
    changeImage: function (a) {
        this.images = [{
            src: a,
            sds: "0,90",
            cnt: this.viewport && null != this.viewport.contrast ? this.viewport.contrast : 1
        }];
        (new Request({
            method: "get",
            url: this.protocol.getMetaDataURL(this.server, this.images[0].src),
            onComplete: function (b) {
                b = this.protocol.parseMetaData(b || alert("Error: No response from server " + this.server));
                this.max_size = b.max_size;
                this.tileSize = b.tileSize;
                this.num_resolutions = b.num_resolutions;
                this.reload();
                this.navcontainer.getElement("img.navimage").src = this.protocol.getThumbnailURL(this.server,
                    a, this.navWin.w)
            }.bind(this),
            onFailure: function () {
                alert("Error: Unable to get image metadata from server!")
            }
        })).send()
    },
    load: function () {
        this.loadoptions ? (this.max_size = this.loadoptions.size, this.tileSize = this.loadoptions.tiles, this.num_resolutions = this.loadoptions.resolutions, this.createWindows()) : (new Request({
            method: "get",
            url: this.protocol.getMetaDataURL(this.server, this.images[0].src),
            onComplete: function (a) {
                a = this.protocol.parseMetaData(a || alert("Error: No response from server " + this.server));
                this.max_size = a.max_size;
                this.tileSize = a.tileSize;
                this.num_resolutions = a.num_resolutions;
                this.createWindows()
            }.bind(this),
            onFailure: function () {
                alert("Error: Unable to get image metadata from server!")
            }
        })).send()
    },
    reflow: function () {
        var a = this.container.getSize();
        this.view.w = a.x;
        this.view.h = a.y;
        this.canvas.setStyles({
            left: this.wid > this.view.w ? -this.view.x : Math.round((this.view.w - this.wid) / 2),
            top: this.hei > this.view.h ? -this.view.y : Math.round((this.view.h - this.hei) / 2)
        });
        this.calculateNavSize();
        this.container.getElements("div.navcontainer, div.navcontainer div.loadBarContainer").setStyle("width",
            this.navWin.w);
        this.showNavWindow && (this.navcontainer && this.navcontainer.setStyles({
            top: Browser.Platform.ios && window.navigator.standalone ? 20 : 10,
            left: this.container.getPosition(this.container).x + this.container.getSize().x - this.navWin.w - 10
        }), this.zone && this.zone.getParent().setStyle("height", this.navWin.h));
        this.scale && (this.updateScale(), pos = this.container.getSize().y - this.container.getElement("div.scale").getSize().y - 10, this.container.getElement("div.scale").setStyles({
            left: 10,
            top: pos
        }));
        this.requestImages();
        this.positionZone();
        this.constrain()
    },
    reload: function () {
        this.canvas.get("morph").cancel();
        this.canvas.getChildren("img").destroy();
        this.tiles.empty();
        this.calculateSizes();
        this.viewport && null != this.viewport.resolution && (this.view.res = this.viewport.resolution, this.wid = this.resolutions[this.view.res].w, this.hei = this.resolutions[this.view.res].h, this.touch.options.limit = {
            x: [this.view.w - this.wid, 0],
            y: [this.view.h - this.hei, 0]
        });
        this.viewport && null != this.viewport.x && null != this.viewport.y ? this.moveTo(this.viewport.x *
            this.wid, this.viewport.y * this.hei) : this.recenter();
        this.canvas.setStyles({
            width: this.wid,
            height: this.hei
        });
        this.reflow();
        this.viewport && null != this.viewport.rotation ? this.rotate(this.viewport.rotation) : this.rotate(0)
    },
    recenter: function () {
        var a = Math.round((this.wid - this.view.w) / 2);
        this.view.x = 0 > a ? 0 : a;
        a = Math.round((this.hei - this.view.h) / 2);
        this.view.y = 0 > a ? 0 : a;
        this.canvas.setStyles({
            left: this.wid > this.view.w ? -this.view.x : Math.round((this.view.w - this.wid) / 2),
            top: this.hei > this.view.h ? -this.view.y : Math.round((this.view.h -
                this.hei) / 2)
        });
        this.constrain()
    },
    constrain: function () {
        var a = this.wid < this.view.w ? [Math.round((this.view.w - this.wid) / 2), Math.round((this.view.w - this.wid) / 2)] : [this.view.w - this.wid, 0],
            b = this.hei < this.view.h ? [Math.round((this.view.h - this.hei) / 2), Math.round((this.view.h - this.hei) / 2)] : [this.view.h - this.hei, 0];
        this.touch.options.limit = {
            x: a,
            y: b
        }
    },
    positionZone: function () {
        if (this.showNavWindow) {
            var a = this.view.x / this.wid * this.navWin.w;
            a > this.navWin.w && (a = this.navWin.w);
            0 > a && (a = 0);
            var b = this.view.y / this.hei *
                this.navWin.h;
            b > this.navWin.h && (b = this.navWin.h);
            0 > b && (b = 0);
            var c = this.view.w / this.wid * this.navWin.w;
            a + c > this.navWin.w && (c = this.navWin.w - a);
            var d = this.view.h / this.hei * this.navWin.h;
            d + b > this.navWin.h && (d = this.navWin.h - b);
            var e = this.zone.offsetHeight - this.zone.clientHeight;
            this.zone.morph({
                left: a,
                top: b + 8,
                width: 0 < c - e ? c - e : 1,
                height: 0 < d - e ? d - e : 1
            })
        }
    }
});
IIPMooViewer.synchronize = function (a) {
    this.sync = a
};
IIPMooViewer.windows = function (a) {
    return !this.sync || !this.sync.contains(a) ? [] : this.sync.filter(function (b) {
        return b != a
    })
};
Browser.buggy = Browser.ie && 9 > Browser.version ? !0 : !1;
var Protocols = {};
Protocols.IIP = new Class({
    getMetaDataURL: function (a, b) {
        return a + "?FIF=" + b + "&obj=IIP,1.0&obj=Max-size&obj=Tile-size&obj=Resolution-number"
    },
    getTileURL: function (a, b, c, d, e, f) {
        return a + "?FIF=" + b + "&CNT=" + e + "&SDS=" + d + "&JTL=" + c + "," + f
    },
    parseMetaData: function (a) {
        var b = a.split("Max-size");
        b[1] || alert("Error: Unexpected response from server " + this.server);
        var c = b[1].split(" "),
            d = {
                w: parseInt(c[0].substring(1, c[0].length)),
                h: parseInt(c[1])
            }, b = a.split("Tile-size"),
            c = b[1].split(" "),
            c = {
                w: parseInt(c[0].substring(1,
                    c[0].length)),
                h: parseInt(c[1])
            }, b = a.split("Resolution-number"),
            a = parseInt(b[1].substring(1, b[1].length));
        return {
            max_size: d,
            tileSize: c,
            num_resolutions: a
        }
    },
    getRegionURL: function (a, b, c, d) {
        return a + "?FIF=" + b + "&WID=" + d + "&RGN=" + (c.x + "," + c.y + "," + c.w + "," + c.h) + "&CVT=jpeg"
    },
    getThumbnailURL: function (a, b, c) {
        return a + "?FIF=" + b + "&WID=" + c + "&QLT=98&CVT=jpeg"
    }
});
IIPMooViewer.implement({
    initAnnotationTips: function () {
        this.annotationTip = null;
        this.annotationsVisible = !0;
        var a = this;
        this.annotations && (this.canvas.addEvent("mouseenter", function () {
            a.annotationsVisible && a.canvas.getElements("div.annotation").removeClass("hidden")
        }), this.canvas.addEvent("mouseleave", function () {
            a.annotationsVisible && a.canvas.getElements("div.annotation").addClass("hidden")
        }))
    },
    createAnnotations: function () {
        if (this.annotations) {
            var a = [],
                b;
            for (b in this.annotations) this.annotations[b].id =
                b, a.push(this.annotations[b]);
            if (0 != a.length) {
                a.sort(function (a, b) {
                    return b.w * b.h - a.w * a.h
                });
                for (b = 0; b < a.length; b++)
                    if (this.wid * (a[b].x + a[b].w) > this.view.x && this.wid * a[b].x < this.view.x + this.view.w && this.hei * (a[b].y + a[b].h) > this.view.y && this.hei * a[b].y < this.view.y + this.view.h) {
                        var c = this,
                            d = "annotation";
                        a[b].category && (d += " " + a[b].category);
                        d = (new Element("div", {
                            id: a[b].id,
                            "class": d,
                            styles: {
                                left: Math.round(this.wid * a[b].x),
                                top: Math.round(this.hei * a[b].y),
                                width: Math.round(this.wid * a[b].w),
                                height: Math.round(this.hei *
                                    a[b].h)
                            }
                        })).inject(this.canvas);
                        !1 == this.annotationsVisible && d.addClass("hidden");
                        "function" == typeof this.editAnnotation && (!0 == a[b].edit ? this.editAnnotation(d) : (c = this, d.addEvent("dblclick", function (a) {
                            (new DOMEvent(a)).stop();
                            c.editAnnotation(this)
                        })));
                        var e = a[b].text;
                        a[b].title && (e = "<h1>" + a[b].title + "</h1>" + e);
                        d.store("tip:text", e)
                    }
                this.annotationTip || (c = this, this.annotationTip = new Tips("div.annotation", {
                    className: "tip",
                    fixed: !0,
                    offset: {
                        x: 30,
                        y: 30
                    },
                    hideDelay: 300,
                    link: "chain",
                    onShow: function (a) {
                        a.setStyles({
                            opacity: a.getStyle("opacity"),
                            display: "block"
                        }).fade(0.9);
                        a.addEvents({
                            mouseleave: function () {
                                this.active = !1;
                                this.fade("out").get("tween").chain(function () {
                                    this.element.setStyle("display", "none")
                                })
                            },
                            mouseenter: function () {
                                this.active = !0
                            }
                        })
                    },
                    onHide: function (a) {
                        a.active || (a.fade("out").get("tween").chain(function () {
                            this.element.setStyle("display", "none")
                        }), a.removeEvents(["mouseenter", "mouseleave"]))
                    }
                }))
            }
        }
    },
    toggleAnnotations: function () {
        var a;
        if (a = this.canvas.getElements("div.annotation")) this.annotationsVisible ? (a.addClass("hidden"),
            this.annotationsVisible = !1, this.showPopUp(IIPMooViewer.lang.annotationsDisabled)) : (a.removeClass("hidden"), this.annotationsVisible = !0)
    },
    destroyAnnotations: function () {
        this.annotationTip && this.annotationTip.detach(this.canvas.getChildren("div.annotation"));
        this.canvas.getChildren("div.annotation").each(function (a) {
            a.eliminate("tip:text");
            a.destroy()
        })
    }
});
IIPMooViewer.implement({
    blend: function (a) {
        this.addEvent("load", function () {
            this.images[1] = {
                src: a[0][0],
                cnt: 1,
                sds: "0,90"
            };
            this.createBlendingInterface();
            a.each(function (a) {
                (new Element("option", {
                    value: a[0],
                    html: a[1]
                })).inject(document.id("baselayer")).clone().inject(document.id("overlay"))
            });
            this.container.removeEvents("mouseenter");
            this.container.removeEvents("mouseleave")
        })
    },
    createBlendingInterface: function () {
        var a = this;
        (new Element("div", {
            "class": "blending",
            html: '<h2 title="<h2>Image Comparison</h2>Select the pair of images you wish<br/>to compare from the menus below.<br/>Use the slider to blend smoothly<br/>between them">Image Comparison</h2><span>Image 1</span><select id="baselayer"></select><br/><br/><span>Move slider to blend between images:</span><br/><div id="area"><div id="knob"></div></div><br/><span>Image 2</span><select id="overlay"></select><br/>'
        })).inject(this.navcontainer);
        new Tips("div.blending h2", {
            className: "tip",
            onShow: function (a) {
                a.setStyle("opacity", 0);
                a.fade(0.7)
            },
            onHide: function (a) {
                a.fade(0)
            }
        });
        var b = new Slider(document.id("area"), document.id("knob"), {
            range: [0, 100],
            onChange: function (b) {
                a.opacity = b / 100;
                a.canvas.getChildren("img.layer1").setStyle("opacity", a.opacity)
            }
        });
        window.addEvent("resize", function () {
            b.autosize()
        });
        document.id("baselayer").addEvent("change", function () {
            a.images[0].src = document.id("baselayer").value;
            a.canvas.getChildren("img.layer0").destroy();
            a.tiles.empty();
            a.requestImages()
        });
        document.id("overlay").addEvent("change", function () {
            a.images[1] = {
                src: document.id("overlay").value,
                cnt: 1
            };
            a.canvas.getChildren("img.layer1").destroy();
            a.tiles.empty();
            a.requestImages()
        })
    }
});
IIPMooViewer.lang = {
    help: "click for help",
    scale: "draggable scale",
    navigate: "To navigate within image: drag image within main window or drag zone within the navigation window or click an are a within navigation window",
    zoomIn: 'To zoom in: double click or use the mouse scroll wheel or simply press the "+" key',
    zoomOut: 'To zoom out: shift double click or use the mouse wheel or press the "-" key',
    rotate: 'To rotate image clockwise: press the "r" key, anti-clockwise: press shift and "r"',
    fullscreen: 'For fullscreen: press the "f" key',
    annotations: 'To toggle any annotations: press the "a" key',
    navigation: 'To show/hide navigation window: press "h" key',
    more: "For more information visit",
    exitFullscreen: 'Press "Esc" to exit fullscreen mode',
    loading: "loading",
    drag: "* Drag to move<br/>* Double Click to show/hide buttons<br/>* Press h to hide",
    annotationsDisabled: 'Annotations disabled<br/>Press "a" to re-enable'
};