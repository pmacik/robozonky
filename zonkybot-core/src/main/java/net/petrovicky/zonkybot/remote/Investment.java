/*
 * Copyright 2016 Lukáš Petrovický
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.petrovicky.zonkybot.remote;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

public class Investment {

    private final Loan loan;
    private final int investedAmount;

    public Investment(Loan loan, int investedAmount) {
        this.loan = loan;
        this.investedAmount = investedAmount;
    }

    @XmlTransient
    public Loan getLoan() {
        return loan;
    }

    @XmlElement
    public int getLoanId() {
        return this.loan.getId();
    }

    @XmlElement(name = "amount")
    public int getInvestedAmount() {
        return investedAmount;
    }
}
