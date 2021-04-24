package edu.iis.mto.testreactor.atm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;


import edu.iis.mto.testreactor.atm.bank.AccountException;
import edu.iis.mto.testreactor.atm.bank.AuthorizationException;
import edu.iis.mto.testreactor.atm.bank.AuthorizationToken;
import edu.iis.mto.testreactor.atm.bank.Bank;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
class ATMachineTest {

    private static final Currency DEFAULT_CURRENCY = Money.DEFAULT_CURRENCY;
    private static final String DEFAULT_CARD_NUMBER = "5123456789104444";
    private static final PinCode DEFAULT_PIN_CODE = PinCode.createPIN(1, 2, 3, 4);
    private static final Card DEFAULT_CARD = Card.create(DEFAULT_CARD_NUMBER);
    private static final AuthorizationToken DEFAULT_TOKEN = AuthorizationToken.create("1234");
    private static final List<Banknote> DEFAULT_CURRENCY_AVAILABLE_BANKNOTES = Banknote.getDescFor(DEFAULT_CURRENCY);
    private static final List<Integer> DEFAULT_CURRENCY_AVAILABLE_DENOMINATIONS = DEFAULT_CURRENCY_AVAILABLE_BANKNOTES
                                                                                          .stream()
                                                                                          .map(Banknote::getDenomination)
                                                                                          .collect(Collectors.toList());

    @Mock
    private Bank bank;

    @Mock
    private MoneyDeposit moneyDeposit;

    private ATMachine atm;

    @BeforeEach
    void setUp() {
        atm = new ATMachine(bank, DEFAULT_CURRENCY);
    }

    @Test
    void itCompiles() {
        assertThat(true, Matchers.equalTo(true));
    }

    @Test
    void shouldThrowExceptionWithWrongCurrencyErrorCodeWhenAmountCurrencyIsInvalid() {
        // given
        Money invalidAmount = new Money(20, Currency.getInstance("EUR"));
        ErrorCode expectedErrorCode = ErrorCode.WRONG_CURRENCY;
        atm.setDeposit(moneyDeposit);
        when(moneyDeposit.getCurrency()).thenReturn(DEFAULT_CURRENCY);

        // when
        ErrorCode actualErrorCode = null;
        try {
            atm.withdraw(DEFAULT_PIN_CODE, DEFAULT_CARD, invalidAmount);
            Assertions.fail("Should throw ATMOperationException when amount is invalid");
        } catch (ATMOperationException e) {
            actualErrorCode = e.getErrorCode();
        }

        // then
        Assertions.assertEquals(expectedErrorCode, actualErrorCode);
    }

    @Test
    void shouldThrowExceptionWithAuthorizationErrorCodeWhenAuthorizationFailed() throws AuthorizationException {
        // given
        Money amount = new Money(20, DEFAULT_CURRENCY);
        atm.setDeposit(moneyDeposit);
        when(moneyDeposit.getCurrency()).thenReturn(DEFAULT_CURRENCY);
        when(bank.autorize(any(String.class), any(String.class))).thenThrow(new AuthorizationException());
        ErrorCode expectedErrorCode = ErrorCode.AHTHORIZATION;

        // when
        ErrorCode actualErrorCode = null;
        try {
            atm.withdraw(DEFAULT_PIN_CODE, DEFAULT_CARD, amount);
            Assertions.fail("Should throw ATMOperationException when client is not authorized");
        } catch (ATMOperationException e) {
            actualErrorCode = e.getErrorCode();
        }

        // then
        Assertions.assertEquals(expectedErrorCode, actualErrorCode);
    }

    @Test
    void shouldThrowExceptionWithWrongAmountErrorCodeWhenAmountIsInvalid() throws AuthorizationException {
        // given
        Money invalidAmount = new Money(1, DEFAULT_CURRENCY);
        atm.setDeposit(moneyDeposit);
        when(moneyDeposit.getCurrency()).thenReturn(DEFAULT_CURRENCY);
        when(bank.autorize(any(String.class), any(String.class))).thenReturn(DEFAULT_TOKEN);
        ErrorCode expectedErrorCode = ErrorCode.WRONG_AMOUNT;

        // when
        ErrorCode actualErrorCode = null;
        try {
            atm.withdraw(DEFAULT_PIN_CODE, DEFAULT_CARD, invalidAmount);
            Assertions.fail("Should throw ATMOperationException when client's amount value is invalid");
        } catch (ATMOperationException e) {
            actualErrorCode = e.getErrorCode();
        }

        // then
        Assertions.assertEquals(expectedErrorCode, actualErrorCode);
    }

    @Test
    void shouldThrowExceptionWithNoFundsOnAccountErrorCodeWhenClientHasTooSmallBalance() throws AuthorizationException, AccountException {
        // given
        final int sampleAvailableCountOfBanknotes = 10;
        Money tooBigWithdrawAmount = new Money(20, DEFAULT_CURRENCY);
        atm.setDeposit(moneyDeposit);
        when(moneyDeposit.getCurrency()).thenReturn(DEFAULT_CURRENCY);
        when(bank.autorize(any(String.class), any(String.class))).thenReturn(DEFAULT_TOKEN);
        when(moneyDeposit.getAvailableCountOf(any(Banknote.class))).thenReturn(sampleAvailableCountOfBanknotes);
        doThrow(new AccountException()).when(bank).charge(any(AuthorizationToken.class), any(Money.class));
        ErrorCode expectedErrorCode = ErrorCode.NO_FUNDS_ON_ACCOUNT;

        // when
        ErrorCode actualErrorCode = null;
        try {
            atm.withdraw(DEFAULT_PIN_CODE, DEFAULT_CARD, tooBigWithdrawAmount);
            Assertions.fail("Should throw ATMOperationException when client's balance is too small");
        } catch (ATMOperationException e) {
            actualErrorCode = e.getErrorCode();
        }

        // then
        Assertions.assertEquals(expectedErrorCode, actualErrorCode);
    }

    @Test
    void shouldReturnExpectedWithdrawal() throws AuthorizationException, AccountException, ATMOperationException {
        // given
        final int sampleCountOfEveryPLNBanknote = 1;
        final int sumOfAllAvailableDenominations = DEFAULT_CURRENCY_AVAILABLE_DENOMINATIONS.stream()
                                                                                           .reduce(0, Integer::sum);
        Money amount = new Money(sumOfAllAvailableDenominations, DEFAULT_CURRENCY);
        atm.setDeposit(moneyDeposit);
        when(moneyDeposit.getCurrency()).thenReturn(DEFAULT_CURRENCY);
        when(bank.autorize(any(String.class), any(String.class))).thenReturn(DEFAULT_TOKEN);
        when(moneyDeposit.getAvailableCountOf(any(Banknote.class))).thenReturn(sampleCountOfEveryPLNBanknote);
        doNothing().when(bank).charge(any(AuthorizationToken.class), any(Money.class));
        doNothing().when(moneyDeposit).release(any(BanknotesPack.class));
        List<BanknotesPack> expectedBanknotesPacks = DEFAULT_CURRENCY_AVAILABLE_BANKNOTES.stream()
                                                                                         .map(b -> BanknotesPack.create(1, b))
                                                                                         .collect(Collectors.toList());
        Withdrawal expectedWithdrawal = Withdrawal.create(expectedBanknotesPacks);

        // when
        Withdrawal actualWithdrawal = atm.withdraw(DEFAULT_PIN_CODE, DEFAULT_CARD, amount);

        // then
        Assertions.assertEquals(expectedWithdrawal, actualWithdrawal);
    }

    @Test
    void shouldReturnEmptyWithdrawalWhenRequestedWithdrawAmountIsZero() throws AuthorizationException, AccountException, ATMOperationException {
        // given
        Money zeroAmount = Money.ZERO;
        atm.setDeposit(moneyDeposit);
        when(moneyDeposit.getCurrency()).thenReturn(DEFAULT_CURRENCY);
        when(bank.autorize(any(String.class), any(String.class))).thenReturn(DEFAULT_TOKEN);
        doNothing().when(bank).charge(any(AuthorizationToken.class), any(Money.class));
        Withdrawal expectedWithdrawal = Withdrawal.create(List.of());

        // when
        Withdrawal actualWithdrawal = atm.withdraw(DEFAULT_PIN_CODE, DEFAULT_CARD, zeroAmount);

        // then
        Assertions.assertEquals(expectedWithdrawal, actualWithdrawal);
    }

    @Test
    void shouldInvokeChargeMethodOnceWhenPerformingBankTransaction() throws AuthorizationException, ATMOperationException, AccountException {
        // given
        Money amount = Money.ZERO;
        atm.setDeposit(moneyDeposit);
        when(moneyDeposit.getCurrency()).thenReturn(DEFAULT_CURRENCY);
        when(bank.autorize(any(String.class), any(String.class))).thenReturn(DEFAULT_TOKEN);

        // when
        atm.withdraw(DEFAULT_PIN_CODE, DEFAULT_CARD, amount);

        // then
        verify(bank, times(1)).charge(DEFAULT_TOKEN, amount);
    }
}
