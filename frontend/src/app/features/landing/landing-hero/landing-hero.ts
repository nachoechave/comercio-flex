import { Component } from '@angular/core';

import { LANDING_CONFIG } from '../landing.config';

@Component({
  selector: 'app-landing-hero',
  templateUrl: './landing-hero.html',
  styleUrl: './landing-hero.scss',
})
export class LandingHero {
  protected readonly config = LANDING_CONFIG;
}
