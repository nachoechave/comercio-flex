import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { LANDING_CONFIG } from '../landing.config';
import { LandingLogo } from '../landing-logo/landing-logo';

@Component({
  selector: 'app-landing-footer',
  imports: [RouterLink, LandingLogo],
  templateUrl: './landing-footer.html',
  styleUrl: './landing-footer.scss',
})
export class LandingFooter {
  protected readonly config = LANDING_CONFIG;
  protected readonly currentYear = new Date().getFullYear();
}
