import { Component, input } from '@angular/core';

@Component({
  selector: 'app-landing-logo',
  templateUrl: './landing-logo.html',
  styleUrl: './landing-logo.scss',
})
export class LandingLogo {
  readonly inverse = input(false);
}
