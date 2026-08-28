import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { LANDING_CONFIG } from '../landing.config';

@Component({
  selector: 'app-landing-cta',
  imports: [RouterLink],
  templateUrl: './landing-cta.html',
  styleUrl: './landing-cta.scss',
})
export class LandingCta {
  protected readonly config = LANDING_CONFIG;
}
