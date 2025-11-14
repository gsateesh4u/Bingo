import { render, screen } from '@testing-library/react';
import App from './App';

test('renders bingo control center header', () => {
  render(<App />);
  expect(screen.getByRole('heading', { name: /bingo control center/i })).toBeInTheDocument();
});
