from manim import *
import numpy as np

# Custom rate functions for absolute compatibility and safety
def slow_into_fast(t):
    return t ** 2.5

def ease_in_out_cubic(t):
    if t < 0.5:
        return 4 * t * t * t
    else:
        return 1 - ((-2 * t + 2) ** 3) / 2

class AndBookOnboarding(Scene):
    def construct(self):
        # 1. Set background color to the app's Dark Coffee Background (#1B1613)
        self.camera.background_color = "#1B1613"

        # 2. Define final text layouts with UI-matching colors
        # Ampersand is larger than Book (140 vs 90 font size) and aligned to baseline (aligned_edge=DOWN)
        amp_final = Text("&", font="Georgia", font_size=140)
        amp_final.set_color_by_gradient("#D9A066", "#E6DFD5")
        
        # Word "Book" uses Cream Latte Foam (#E6DFD5)
        book_text_final = Text("Book", font="Georgia", font_size=90, color="#E6DFD5")
        
        logo_text = VGroup(amp_final, book_text_final).arrange(RIGHT, buff=0.15, aligned_edge=DOWN).center()
        logo_text.shift(1.2 * DOWN)  # Shift down to balance the layout

        # Initial ampersand in the center (significantly larger for impact)
        amp = Text("&", font="Georgia", font_size=200)
        amp.set_color_by_gradient("#D9A066", "#E6DFD5")
        amp.move_to(ORIGIN)

        # 3. Phase 1: Draw the font ampersand in the center (standard Write draws outline then fills)
        self.play(
            Write(amp),
            run_time=2.2,
            rate_func=slow_into_fast
        )
        self.wait(0.4)

        # 4. Phase 2: Slide the ampersand to its final position
        self.play(
            amp.animate.move_to(amp_final.get_center()).scale(140/200),
            run_time=1.5,
            rate_func=ease_in_out_cubic
        )
        self.wait(0.2)

        # 5. Phase 3: Create the elegant book icon matching the UI theme
        # Spine line in Golden Caramel Accent (#D9A066)
        spine = Line([0, -1.6, 0], [0, 1.6, 0], stroke_width=4, color="#D9A066")

        # Book Cover: Dark Warm Espresso Card (#241D1A) with Editorial Outline Border (#332A24)
        cover = Rectangle(width=4.3, height=3.3, fill_color="#241D1A", fill_opacity=1, stroke_color="#332A24", stroke_width=2.5)
        cover.move_to(ORIGIN)

        # Pages: Bright Organic Warm Paper White (#FAF6F0) with borders (#E5DFD5)
        left_page = Rectangle(width=2.0, height=3.0, fill_color="#FAF6F0", fill_opacity=1, stroke_color="#E5DFD5", stroke_width=1.5)
        left_page.shift(1.02 * LEFT)
        
        right_page = Rectangle(width=2.0, height=3.0, fill_color="#FAF6F0", fill_opacity=1, stroke_color="#E5DFD5", stroke_width=1.5)
        right_page.shift(1.02 * RIGHT)

        # Subtle text lines on the pages in Muted Charcoal Cocoa (#6E655C)
        text_lines_l = VGroup(*[
            Line([-1.8, y, 0], [-0.2, y, 0], color="#6E655C", stroke_width=2)
            for y in [0.9, 0.4, -0.1, -0.6, -1.1]
        ])
        
        text_lines_r = VGroup(*[
            Line([0.2, y, 0], [1.8, y, 0], color="#6E655C", stroke_width=2)
            for y in [0.9, 0.4, -0.1, -0.6, -1.1]
        ])

        # Group pages and lines together so they open/stretch in sync!
        left_page_full = VGroup(left_page, text_lines_l)
        right_page_full = VGroup(right_page, text_lines_r)

        book_icon = VGroup(cover, left_page_full, right_page_full, spine)
        book_icon.scale(0.8)
        book_icon.next_to(logo_text, UP, buff=0.8)

        # Position helpers for opening animation
        spine_center = spine.get_center()

        # Closed states of the book components (width stretched to near-zero around the spine)
        cover_closed = cover.copy().stretch(0.001, 0, about_point=spine_center)
        left_page_full_closed = left_page_full.copy().stretch(0.001, 0, about_point=spine_center)
        right_page_full_closed = right_page_full.copy().stretch(0.001, 0, about_point=spine_center)

        # Reveal "Book" text and fold open the book pages (together with their lines)
        self.play(
            Write(book_text_final),
            Create(spine),
            ReplacementTransform(cover_closed, cover),
            ReplacementTransform(left_page_full_closed, left_page_full),
            ReplacementTransform(right_page_full_closed, right_page_full),
            run_time=1.8,
            rate_func=ease_in_out_cubic
        )
        self.wait(0.4)

        # 6. Subtitle reveal in Muted Roasted Latte (#948A80)
        subtitle = Text(
            "A world-class minimalist offline reader",
            font="sans-serif",
            font_size=24,
            color="#948A80"
        )
        subtitle.next_to(logo_text, DOWN, buff=0.8)

        self.play(
            FadeIn(subtitle, shift=UP * 0.15),
            run_time=1.5,
            rate_func=smooth
        )
        self.wait(2.5)
